import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";
import * as crypto from "crypto";

initializeApp();

const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-goprime-f9c8093d-00cc-4e26-a4d1-18f0cd1cd23a";

const db = getFirestore(DATABASE_ID);

// Secrets managed in Google Cloud Secret Manager via:
// firebase functions:secrets:set WHATSAPP_ACCESS_TOKEN
// firebase functions:secrets:set WHATSAPP_PHONE_NUMBER_ID
// firebase functions:secrets:set MOBILE_MONEY_WEBHOOK_SECRET
const whatsappAccessToken = defineSecret("WHATSAPP_ACCESS_TOKEN");
const whatsappPhoneNumberId = defineSecret("WHATSAPP_PHONE_NUMBER_ID");
const mobileMoneyWebhookSecret = defineSecret("MOBILE_MONEY_WEBHOOK_SECRET");

const BOOTSTRAPPED_ADMIN_EMAIL = "alherilionel338@gmail.com";
const DEFAULT_ADMIN_WHATSAPP_PHONE = "+243974685125";

interface CartLineInput {
  productId: string;
  quantity: number;
}

interface PlaceOrderPayload {
  customerName: string;
  customerPhone: string;
  fulfillmentMode: "DELIVERY" | "PICKUP";
  districtId?: string;
  deliveryAddress: string;
  paymentMethod: "CASH" | "MOBILE_MONEY";
  paymentProvider?: string;
  paymentPhone?: string;
  paymentReference?: string;
  notes?: string;
  items: CartLineInput[];
}

interface AdminRoleVerificationResult {
  uid: string;
  isAdmin: boolean;
  role: string;
  active: boolean;
  verifiedVia: "BOOTSTRAPPED_EMAIL" | "ADMINS_COLLECTION" | "CUSTOM_CLAIMS" | "NONE";
  canWriteProducts: boolean;
}

/**
 * Vérifie côté serveur si un utilisateur possède le rôle administrateur actif.
 * Contrôle : email administrateur vérifié, collection `admins/{uid}`, `users/{uid}` (active != false)
 * et custom claims Firebase Auth.
 */
async function verifyUserAdminStatus(
  uid: string,
  email?: string,
  emailVerified?: boolean
): Promise<AdminRoleVerificationResult> {
  if (emailVerified && email === BOOTSTRAPPED_ADMIN_EMAIL) {
    return {
      uid,
      isAdmin: true,
      role: "ADMIN",
      active: true,
      verifiedVia: "BOOTSTRAPPED_EMAIL",
      canWriteProducts: true,
    };
  }

  const [adminDoc, userDoc] = await Promise.all([
    db.collection("admins").doc(uid).get(),
    db.collection("users").doc(uid).get(),
  ]);

  const userData = userDoc.exists ? userDoc.data() : undefined;
  const active = userData?.active !== false;
  const firestoreRole = String(userData?.role || "CLIENT").toUpperCase();

  let claimIsAdmin = false;
  try {
    const authUser = await getAuth().getUser(uid);
    claimIsAdmin = String(authUser.customClaims?.role || "").toUpperCase() === "ADMIN";
  } catch (err) {
    logger.debug(`Impossible de lire les customClaims pour ${uid}`, err);
  }

  if (!active) {
    return {
      uid,
      isAdmin: false,
      role: firestoreRole,
      active: false,
      verifiedVia: "NONE",
      canWriteProducts: false,
    };
  }

  if (adminDoc.exists) {
    return {
      uid,
      isAdmin: true,
      role: "ADMIN",
      active: true,
      verifiedVia: "ADMINS_COLLECTION",
      canWriteProducts: true,
    };
  }

  if (claimIsAdmin && firestoreRole === "ADMIN") {
    return {
      uid,
      isAdmin: true,
      role: "ADMIN",
      active: true,
      verifiedVia: "CUSTOM_CLAIMS",
      canWriteProducts: true,
    };
  }

  return {
    uid,
    isAdmin: false,
    role: firestoreRole,
    active: true,
    verifiedVia: "NONE",
    canWriteProducts: false,
  };
}

async function assertAdminCaller(uid: string, email?: string, emailVerified?: boolean): Promise<void> {
  const status = await verifyUserAdminStatus(uid, email, emailVerified);
  if (!status.isAdmin) {
    throw new HttpsError("permission-denied", "Accès réservé aux administrateurs GoPrime.");
  }
}

interface WhatsAppNotificationPayload {
  messaging_product: "whatsapp";
  recipient_type: "individual";
  to: string;
  type: "template";
  template: {
    name: string;
    language: { code: string };
    components: Array<{
      type: "body";
      parameters: Array<{ type: "text"; text: string }>;
    }>;
  };
}

interface ComputedOrderTotals {
  subtotalCdf: number;
  deliveryFeeCdf: number;
  totalCdf: number;
  districtName: string;
  recalculated: boolean;
}

/**
 * Recalcule côté serveur le sous-total des articles et les frais de livraison
 * selon le quartier (collection `deliveryZones`) pour garantir l'intégrité des montants.
 */
async function computeAuthoritativeOrderTotals(
  order: FirebaseFirestore.DocumentData
): Promise<ComputedOrderTotals> {
  const rawItems = Array.isArray(order.items) ? order.items : [];
  let serverSubtotalCdf = 0;

  for (const rawItem of rawItems) {
    const productId = String((rawItem as Record<string, unknown>).productId || "");
    const qty = Math.max(1, Math.floor(Number((rawItem as Record<string, unknown>).quantity || 1)));
    if (!productId) continue;

    const prodSnap = await db.collection("products").doc(productId).get();
    if (prodSnap.exists) {
      const unitPriceCdf = Number(prodSnap.data()?.priceCdf || 0);
      serverSubtotalCdf += unitPriceCdf * qty;
    } else {
      serverSubtotalCdf += Number((rawItem as Record<string, unknown>).unitPriceCdf || 0) * qty;
    }
  }

  const fulfillmentMode = order.fulfillmentMode === "PICKUP" ? "PICKUP" : "DELIVERY";
  let serverDeliveryFeeCdf = 0;
  let districtName = String(order.districtName || "Point de retrait GoPrime");

  if (fulfillmentMode === "PICKUP") {
    serverDeliveryFeeCdf = 0;
    districtName = "Point de retrait GoPrime";
  } else {
    const districtId = String(order.districtId || "").trim();
    const rawDistrictName = String(order.districtName || "").trim();
    if (districtId) {
      const zoneSnap = await db.collection("deliveryZones").doc(districtId).get();
      if (zoneSnap.exists) {
        serverDeliveryFeeCdf = Number(zoneSnap.data()?.feeCdf || 0);
        districtName = String(zoneSnap.data()?.name || districtName);
      } else if (rawDistrictName) {
        const byNameSnap = await db
          .collection("deliveryZones")
          .where("name", "==", rawDistrictName)
          .limit(1)
          .get();
        if (!byNameSnap.empty) {
          const zoneData = byNameSnap.docs[0].data();
          serverDeliveryFeeCdf = Number(zoneData.feeCdf || 0);
          districtName = String(zoneData.name || districtName);
        }
      }
    } else if (rawDistrictName) {
      const byNameSnap = await db
        .collection("deliveryZones")
        .where("name", "==", rawDistrictName)
        .limit(1)
        .get();
      if (!byNameSnap.empty) {
        const zoneData = byNameSnap.docs[0].data();
        serverDeliveryFeeCdf = Number(zoneData.feeCdf || 0);
        districtName = String(zoneData.name || districtName);
      } else {
        serverDeliveryFeeCdf = Math.max(0, Number(order.deliveryFeeCdf || 0));
      }
    } else {
      serverDeliveryFeeCdf = Math.max(0, Number(order.deliveryFeeCdf || 0));
    }
  }

  const subtotalCdf = serverSubtotalCdf > 0 ? serverSubtotalCdf : Number(order.subtotalCdf || 0);
  const totalCdf = subtotalCdf + serverDeliveryFeeCdf;
  const recalculated =
    subtotalCdf !== Number(order.subtotalCdf || 0) ||
    serverDeliveryFeeCdf !== Number(order.deliveryFeeCdf || 0) ||
    totalCdf !== Number(order.totalCdf || 0);

  return {
    subtotalCdf,
    deliveryFeeCdf: serverDeliveryFeeCdf,
    totalCdf,
    districtName,
    recalculated,
  };
}

function normalizeWhatsAppPhone(rawPhone: string): string {
  const digits = rawPhone.replace(/[^0-9]/g, "");
  if (!digits) return "";
  if (digits.startsWith("0") && digits.length >= 9 && digits.length <= 10) {
    return `243${digits.slice(1)}`;
  }
  return digits.length >= 8 ? digits : "";
}

function formatOrderItemsSummary(rawItems: unknown): string {
  const items = Array.isArray(rawItems) ? rawItems : [];
  if (items.length === 0) return "Articles GoPrime";
  const parts = items.slice(0, 3).map((item) => {
    const rec = item as Record<string, unknown>;
    const qty = Math.max(1, Math.floor(Number(rec.quantity || 1)));
    const name = String(rec.productName || "Article").trim();
    return `${qty}x ${name}`;
  });
  const suffix = items.length > 3 ? ` (+${items.length - 3})` : "";
  return `${parts.join(", ")}${suffix}`.slice(0, 160);
}

function formatPaymentSummary(order: FirebaseFirestore.DocumentData): string {
  const method = order.paymentMethod === "MOBILE_MONEY"
    ? `Mobile Money${order.paymentProvider ? ` (${String(order.paymentProvider)})` : ""}`
    : "Espèces";
  const statusMap: Record<string, string> = {
    PAID: "Payé (vérifié)",
    PENDING_VERIFICATION: "Vérification en attente",
    PENDING: "À encaisser",
    FAILED: "Échec paiement",
  };
  const statusLabel = statusMap[String(order.paymentStatus || "PENDING")] || "À encaisser";
  return `${method} • ${statusLabel}`;
}

/**
 * Prépare la structure JSON conforme à l'API officielle Meta WhatsApp Business Cloud (v21.0).
 */
function buildWhatsAppOrderNotificationPayload(params: {
  toPhone: string;
  templateName: string;
  languageCode: string;
  orderNumber: string;
  customerLabel: string;
  totalLabel: string;
  transactionDetails: string;
}): WhatsAppNotificationPayload {
  const normalizedPhone = normalizeWhatsAppPhone(params.toPhone);
  return {
    messaging_product: "whatsapp",
    recipient_type: "individual",
    to: normalizedPhone,
    type: "template",
    template: {
      name: params.templateName,
      language: { code: params.languageCode || "fr" },
      components: [
        {
          type: "body",
          parameters: [
            { type: "text", text: params.orderNumber },
            { type: "text", text: params.customerLabel },
            { type: "text", text: params.totalLabel },
            { type: "text", text: params.transactionDetails },
          ],
        },
      ],
    },
  };
}

async function sendWhatsAppTemplateWithRetry(params: {
  phoneNumberId: string;
  accessToken: string;
  payload: WhatsAppNotificationPayload;
}): Promise<{ ok: boolean; statusCode: number; messageId?: string; errorDetail?: string }> {
  const endpoint = `https://graph.facebook.com/v21.0/${params.phoneNumberId}/messages`;

  let lastError = "Unknown error";
  let lastStatus = 0;

  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${params.accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(params.payload),
      });

      lastStatus = response.status;
      const json = (await response.json()) as Record<string, unknown>;

      if (response.ok) {
        const messages = json.messages as Array<{ id?: string }> | undefined;
        return {
          ok: true,
          statusCode: response.status,
          messageId: messages?.[0]?.id,
        };
      }

      lastError = JSON.stringify(json).slice(0, 350);
      // Retry only on rate limit or transient 5xx server failures
      if (response.status !== 429 && response.status < 500) {
        break;
      }
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
    }

    if (attempt < 3) {
      await new Promise((resolve) => setTimeout(resolve, attempt * 1000));
    }
  }

  return {
    ok: false,
    statusCode: lastStatus,
    errorDetail: lastError,
  };
}

/**
 * Déclencheur Firestore (`onDocumentCreated` sur `orders/{orderId}`) :
 * 1) Recalcule côté serveur les frais de livraison basés sur le quartier et le total CDF.
 * 2) Prépare la structure officielle de notification WhatsApp Business Cloud API (Admin & Client).
 * 3) Envoie la notification aux administrateurs et au client avec les détails de la transaction.
 */
export const onOrderCreatedNotifyAdmins = onDocumentCreated(
  {
    document: "orders/{orderId}",
    database: DATABASE_ID,
    secrets: [whatsappAccessToken, whatsappPhoneNumberId],
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const orderId = event.params.orderId;
    const order = snapshot.data();
    if (order.whatsappNotificationStatus === "SENT" && order.customerWhatsappStatus === "SENT") {
      return;
    }

    const orderNumber = String(order.orderNumber || orderId);
    const customerName = String(order.customerName || "Client");
    const customerPhoneRaw = String(order.customerPhone || "");
    const normalizedCustomerPhone = normalizeWhatsAppPhone(customerPhoneRaw);

    // 1. Calcul autoritaire côté serveur des frais de livraison selon le quartier
    const computed = await computeAuthoritativeOrderTotals(order);
    if (computed.recalculated) {
      logger.warn(`Recalcul serveur des montants pour ${orderNumber}`, {
        orderId,
        subtotalCdf: computed.subtotalCdf,
        deliveryFeeCdf: computed.deliveryFeeCdf,
        totalCdf: computed.totalCdf,
      });
      await snapshot.ref.update({
        subtotalCdf: computed.subtotalCdf,
        deliveryFeeCdf: computed.deliveryFeeCdf,
        totalCdf: computed.totalCdf,
        districtName: computed.districtName,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    const fulfillmentLabel =
      order.fulfillmentMode === "PICKUP"
        ? "Retrait en point GoPrime (0 CDF)"
        : `Livraison ${computed.districtName} (${computed.deliveryFeeCdf.toLocaleString("fr-FR")} CDF)`;
    const itemsSummary = formatOrderItemsSummary(order.items);
    const paymentSummary = formatPaymentSummary(order);
    const totalLabel = `${computed.totalCdf.toLocaleString("fr-FR")} CDF (${paymentSummary})`;
    const transactionDetails = `${fulfillmentLabel} • ${itemsSummary}`.slice(0, 260);

    const accessToken = whatsappAccessToken.value().trim();
    const phoneNumberId = whatsappPhoneNumberId.value().trim();
    const settingsSnap = await db.collection("settings").doc("whatsapp").get();
    const settings = settingsSnap.data() || {};

    const enabled = settingsSnap.exists ? Boolean(settings.enabled) : true;
    const notifyCustomerOnOrder = settings.notifyCustomerOnOrder !== false;
    const configuredAdminRecipients = Array.isArray(settings.adminWhatsappPhones)
      ? (settings.adminWhatsappPhones as string[]).filter((p) => normalizeWhatsAppPhone(p).length >= 8)
      : [];
    const adminRecipients = configuredAdminRecipients.length > 0
      ? configuredAdminRecipients
      : [DEFAULT_ADMIN_WHATSAPP_PHONE];
    const adminTemplateName = String(settings.templateName || "goprime_new_order");
    const customerTemplateName = String(settings.customerTemplateName || "goprime_order_confirmation");
    const languageCode = String(settings.languageCode || "fr");

    if (!enabled || !accessToken || !phoneNumberId) {
      await snapshot.ref.update({
        whatsappNotificationStatus: "NOT_CONFIGURED",
        customerWhatsappStatus: "NOT_CONFIGURED",
        updatedAt: FieldValue.serverTimestamp(),
      });
      await Promise.all([
        db.collection("notificationLogs").add({
          orderId,
          orderNumber,
          channel: "WHATSAPP_CLOUD_API",
          recipientRole: "ADMIN",
          recipient: adminRecipients.join(", ") || "Aucun admin",
          status: "NOT_CONFIGURED",
          detail: `Structure prête (${adminTemplateName}, ${totalLabel}, ${transactionDetails}). Secrets Meta ou activation manquants.`,
          createdAt: FieldValue.serverTimestamp(),
        }),
        db.collection("notificationLogs").add({
          orderId,
          orderNumber,
          channel: "WHATSAPP_CLOUD_API",
          recipientRole: "CLIENT",
          recipient: customerPhoneRaw || "Aucun téléphone client",
          status: "NOT_CONFIGURED",
          detail: `Confirmation client prête (${customerTemplateName}, ${totalLabel}, ${transactionDetails}). Secrets Meta ou activation manquants.`,
          createdAt: FieldValue.serverTimestamp(),
        }),
      ]);
      return;
    }

    // 2. Notification aux administrateurs
    let adminStatus = "NOT_CONFIGURED";
    if (adminRecipients.length > 0) {
      let anyAdminOk = false;
      for (const phone of adminRecipients) {
        const payload = buildWhatsAppOrderNotificationPayload({
          toPhone: phone,
          templateName: adminTemplateName,
          languageCode,
          orderNumber,
          customerLabel: `${customerName} (${customerPhoneRaw})`,
          totalLabel,
          transactionDetails,
        });

        const result = await sendWhatsAppTemplateWithRetry({
          phoneNumberId,
          accessToken,
          payload,
        });
        if (result.ok) anyAdminOk = true;

        await db.collection("notificationLogs").add({
          orderId,
          orderNumber,
          channel: "WHATSAPP_CLOUD_API",
          recipientRole: "ADMIN",
          recipient: phone,
          status: result.ok ? "SENT" : "FAILED",
          detail: result.ok
            ? `Admin notifié (ID: ${result.messageId || "ok"}, HTTP ${result.statusCode})`
            : `HTTP ${result.statusCode}: ${result.errorDetail || "Erreur API"}`,
          createdAt: FieldValue.serverTimestamp(),
        });
      }
      adminStatus = anyAdminOk ? "SENT" : "FAILED";
    } else {
      await db.collection("notificationLogs").add({
        orderId,
        orderNumber,
        channel: "WHATSAPP_CLOUD_API",
        recipientRole: "ADMIN",
        recipient: "Aucun admin configuré",
        status: "NOT_CONFIGURED",
        detail: `Aucun numéro WhatsApp administrateur valide configuré dans Paramètres.`,
        createdAt: FieldValue.serverTimestamp(),
      });
    }

    // 3. Notification automatique au client avec détails de la transaction
    let customerStatus = "NOT_CONFIGURED";
    if (notifyCustomerOnOrder && normalizedCustomerPhone.length >= 8) {
      const customerPayload = buildWhatsAppOrderNotificationPayload({
        toPhone: normalizedCustomerPhone,
        templateName: customerTemplateName,
        languageCode,
        orderNumber,
        customerLabel: customerName,
        totalLabel,
        transactionDetails,
      });

      const customerResult = await sendWhatsAppTemplateWithRetry({
        phoneNumberId,
        accessToken,
        payload: customerPayload,
      });
      customerStatus = customerResult.ok ? "SENT" : "FAILED";

      await db.collection("notificationLogs").add({
        orderId,
        orderNumber,
        channel: "WHATSAPP_CLOUD_API",
        recipientRole: "CLIENT",
        recipient: customerPhoneRaw,
        status: customerStatus,
        detail: customerResult.ok
          ? `Client notifié (ID: ${customerResult.messageId || "ok"}, HTTP ${customerResult.statusCode})`
          : `HTTP ${customerResult.statusCode}: ${customerResult.errorDetail || "Erreur API"}`,
        createdAt: FieldValue.serverTimestamp(),
      });
    } else {
      await db.collection("notificationLogs").add({
        orderId,
        orderNumber,
        channel: "WHATSAPP_CLOUD_API",
        recipientRole: "CLIENT",
        recipient: customerPhoneRaw || "Numéro absent",
        status: "NOT_CONFIGURED",
        detail: notifyCustomerOnOrder
          ? "Numéro téléphone client manquant ou invalide."
          : "Notification client désactivée dans les paramètres admin.",
        createdAt: FieldValue.serverTimestamp(),
      });
    }

    await snapshot.ref.update({
      whatsappNotificationStatus: adminStatus,
      customerWhatsappStatus: customerStatus,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
);

/**
 * Déclencheur Firestore (`onDocumentUpdated` sur `orders/{orderId}`) :
 * Notifie automatiquement le client par WhatsApp API lorsque sa commande est validée,
 * mise en livraison, prête au retrait, terminée, annulée ou lorsque le paiement est confirmé.
 */
export const onOrderStatusUpdatedNotifyCustomer = onDocumentUpdated(
  {
    document: "orders/{orderId}",
    database: DATABASE_ID,
    secrets: [whatsappAccessToken, whatsappPhoneNumberId],
  },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after || !event.data) return;

    const statusChanged = before.status !== after.status;
    const paymentConfirmed = before.paymentStatus !== "PAID" && after.paymentStatus === "PAID";
    if (!statusChanged && !paymentConfirmed) return;

    const notifiableStatuses = new Set([
      "ACCEPTED",
      "READY_FOR_PICKUP",
      "OUT_FOR_DELIVERY",
      "COMPLETED",
      "CANCELLED",
      "REJECTED",
    ]);
    if (statusChanged && !notifiableStatuses.has(String(after.status || "")) && !paymentConfirmed) {
      return;
    }

    const orderId = event.params.orderId;
    const orderNumber = String(after.orderNumber || orderId);
    const customerName = String(after.customerName || "Client");
    const customerPhoneRaw = String(after.customerPhone || "");
    const normalizedCustomerPhone = normalizeWhatsAppPhone(customerPhoneRaw);
    if (normalizedCustomerPhone.length < 8) return;

    const settingsSnap = await db.collection("settings").doc("whatsapp").get();
    const settings = settingsSnap.data() || {};
    const enabled = settingsSnap.exists ? Boolean(settings.enabled) : true;
    const notifyCustomerOnOrder = settings.notifyCustomerOnOrder !== false;
    const accessToken = whatsappAccessToken.value().trim();
    const phoneNumberId = whatsappPhoneNumberId.value().trim();
    if (!enabled || !notifyCustomerOnOrder || !accessToken || !phoneNumberId) {
      return;
    }

    const statusLabels: Record<string, string> = {
      ACCEPTED: "Commande validée et acceptée",
      READY_FOR_PICKUP: "Commande prête au retrait",
      OUT_FOR_DELIVERY: "Commande en cours de livraison",
      COMPLETED: "Commande livrée / terminée",
      CANCELLED: "Commande annulée",
      REJECTED: "Commande refusée",
    };

    const statusText = statusLabels[String(after.status || "")] || `Statut : ${String(after.status || "")}`;
    const paymentSummary = formatPaymentSummary(after);
    const totalLabel = `${Number(after.totalCdf || 0).toLocaleString("fr-FR")} CDF (${paymentSummary})`;
    const transactionDetails = `${statusText} • ${String(after.districtName || "GoPrime")} • ${formatOrderItemsSummary(after.items)}`.slice(0, 260);

    const payload = buildWhatsAppOrderNotificationPayload({
      toPhone: normalizedCustomerPhone,
      templateName: String(settings.customerTemplateName || "goprime_order_confirmation"),
      languageCode: String(settings.languageCode || "fr"),
      orderNumber,
      customerLabel: customerName,
      totalLabel,
      transactionDetails,
    });

    const result = await sendWhatsAppTemplateWithRetry({
      phoneNumberId,
      accessToken,
      payload,
    });

    await db.collection("notificationLogs").add({
      orderId,
      orderNumber,
      channel: "WHATSAPP_CLOUD_API",
      recipientRole: "CLIENT",
      recipient: customerPhoneRaw,
      status: result.ok ? "SENT" : "FAILED",
      detail: result.ok
        ? `Mise à jour client (${statusText}) envoyée (ID: ${result.messageId || "ok"})`
        : `Mise à jour client HTTP ${result.statusCode}: ${result.errorDetail || "Erreur API"}`,
      createdAt: FieldValue.serverTimestamp(),
    });

    await event.data.after.ref.update({
      customerWhatsappStatus: result.ok ? "SENT" : "FAILED",
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
);

/**
 * Calcule automatiquement côté serveur (Cloud Function) le sous-total produits,
 * les frais de livraison selon le quartier (`deliveryZones`) et le total en CDF.
 */
export const calculateOrderQuote = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentification requise.");
  }

  const payload = (request.data || {}) as {
    fulfillmentMode?: string;
    districtId?: string;
    items?: CartLineInput[];
  };

  const fulfillmentMode = payload.fulfillmentMode === "PICKUP" ? "PICKUP" : "DELIVERY";
  const items = Array.isArray(payload.items) ? payload.items : [];
  if (items.length === 0 || items.length > 50) {
    throw new HttpsError("invalid-argument", "Le panier est invalide.");
  }

  let subtotalCdf = 0;
  for (const item of items) {
    const qty = Math.floor(Number(item.quantity));
    if (!item.productId || qty < 1 || qty > 99) {
      throw new HttpsError("invalid-argument", "Quantité produit invalide.");
    }
    const prodSnap = await db.collection("products").doc(item.productId).get();
    if (!prodSnap.exists || prodSnap.data()?.active !== true) {
      throw new HttpsError("failed-precondition", `Produit indisponible: ${item.productId}`);
    }
    const unitPriceCdf = Number(prodSnap.data()?.priceCdf || 0);
    subtotalCdf += unitPriceCdf * qty;
  }

  let deliveryFeeCdf = 0;
  let districtName = "Point de retrait GoPrime";
  let estimatedMinutes = 0;

  if (fulfillmentMode === "DELIVERY") {
    const districtId = String(payload.districtId || "").trim();
    if (!districtId) {
      throw new HttpsError("invalid-argument", "Quartier de livraison requis.");
    }
    const zoneSnap = await db.collection("deliveryZones").doc(districtId).get();
    if (!zoneSnap.exists || zoneSnap.data()?.active !== true) {
      throw new HttpsError("failed-precondition", "Zone de livraison indisponible.");
    }
    const zoneData = zoneSnap.data()!;
    deliveryFeeCdf = Number(zoneData.feeCdf || 0);
    districtName = String(zoneData.name || "");
    estimatedMinutes = Number(zoneData.estimatedMinutes || 30);
  }

  return {
    fulfillmentMode,
    districtName,
    estimatedMinutes,
    subtotalCdf,
    deliveryFeeCdf,
    totalCdf: subtotalCdf + deliveryFeeCdf,
    source: "CLOUD_FUNCTION",
  };
});

/**
 * Server-authoritative order placement:
 * Calculates product prices, delivery fees in CDF, and decrements stock inside a transaction.
 */
export const placeOrder = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentification requise.");
  }

  const uid = request.auth.uid;
  const payload = request.data as PlaceOrderPayload;
  if (!payload || !Array.isArray(payload.items) || payload.items.length === 0 || payload.items.length > 50) {
    throw new HttpsError("invalid-argument", "Le panier est invalide.");
  }

  const fulfillmentMode = payload.fulfillmentMode === "PICKUP" ? "PICKUP" : "DELIVERY";
  const paymentMethod = payload.paymentMethod === "MOBILE_MONEY" ? "MOBILE_MONEY" : "CASH";

  const orderRef = db.collection("orders").doc();
  const orderNumber = `GP-${Date.now().toString().slice(-6)}`;

  const result = await db.runTransaction(async (tx) => {
    let deliveryFeeCdf = 0;
    let districtName = "Point de retrait GoPrime";
    let districtId = "";

    if (fulfillmentMode === "DELIVERY") {
      if (!payload.districtId) {
        throw new HttpsError("invalid-argument", "Quartier de livraison requis.");
      }
      const zoneSnap = await tx.get(db.collection("deliveryZones").doc(payload.districtId));
      if (!zoneSnap.exists || zoneSnap.data()?.active !== true) {
        throw new HttpsError("failed-precondition", "Zone de livraison indisponible.");
      }
      deliveryFeeCdf = Number(zoneSnap.data()?.feeCdf || 0);
      districtName = String(zoneSnap.data()?.name || "");
      districtId = zoneSnap.id;
    }

    let subtotalCdf = 0;
    const verifiedItems: Array<Record<string, unknown>> = [];

    for (const item of payload.items) {
      const qty = Math.floor(Number(item.quantity));
      if (!item.productId || qty < 1 || qty > 99) {
        throw new HttpsError("invalid-argument", "Quantité produit invalide.");
      }
      const prodRef = db.collection("products").doc(item.productId);
      const prodSnap = await tx.get(prodRef);
      if (!prodSnap.exists || prodSnap.data()?.active !== true) {
        throw new HttpsError("failed-precondition", `Produit indisponible: ${item.productId}`);
      }
      const prod = prodSnap.data()!;
      const stock = Number(prod.stock || 0);
      const unitPriceCdf = Number(prod.priceCdf || 0);
      if (stock < qty) {
        throw new HttpsError("failed-precondition", `Stock insuffisant pour ${prod.name}.`);
      }

      tx.update(prodRef, {
        stock: stock - qty,
        updatedAt: FieldValue.serverTimestamp(),
      });

      subtotalCdf += unitPriceCdf * qty;
      verifiedItems.push({
        productId: prodSnap.id,
        productName: String(prod.name),
        unitPriceCdf,
        quantity: qty,
        lineTotalCdf: unitPriceCdf * qty,
      });
    }

    const totalCdf = subtotalCdf + deliveryFeeCdf;
    const paymentStatus = paymentMethod === "MOBILE_MONEY" ? "PENDING_VERIFICATION" : "PENDING";

    const orderDoc: Record<string, unknown> = {
      orderNumber,
      userId: uid,
      customerName: String(payload.customerName || "").trim().slice(0, 80),
      customerPhone: String(payload.customerPhone || "").trim().slice(0, 24),
      fulfillmentMode,
      districtName,
      deliveryAddress: String(payload.deliveryAddress || "").trim().slice(0, 240),
      items: verifiedItems,
      subtotalCdf,
      deliveryFeeCdf,
      totalCdf,
      paymentMethod,
      paymentStatus,
      status: "PENDING",
      whatsappNotificationStatus: "PENDING",
      customerWhatsappStatus: "PENDING",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    };

    if (districtId) orderDoc.districtId = districtId;
    if (payload.paymentProvider) orderDoc.paymentProvider = payload.paymentProvider.slice(0, 40);
    if (payload.paymentPhone) orderDoc.paymentPhone = payload.paymentPhone.slice(0, 24);
    if (payload.paymentReference) orderDoc.paymentReference = payload.paymentReference.slice(0, 80);
    if (payload.notes) orderDoc.notes = payload.notes.slice(0, 300);

    tx.set(orderRef, orderDoc);
    return { orderId: orderRef.id, orderNumber, totalCdf };
  });

  return result;
});

/**
 * Admin-only server-side role assignment with Auth custom claims + Firestore marker sync.
 */
export const setUserRole = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentification requise.");
  }
  await assertAdminCaller(
    request.auth.uid,
    request.auth.token.email,
    Boolean(request.auth.token.email_verified)
  );

  const { targetUid, role } = request.data as { targetUid: string; role: "CLIENT" | "DRIVER" | "ADMIN" };
  if (!targetUid || !["CLIENT", "DRIVER", "ADMIN"].includes(role)) {
    throw new HttpsError("invalid-argument", "Paramètres de rôle invalides.");
  }

  const userRef = db.collection("users").doc(targetUid);
  const userSnap = await userRef.get();
  if (!userSnap.exists) {
    throw new HttpsError("not-found", "Utilisateur introuvable.");
  }

  await getAuth().setCustomUserClaims(targetUid, { role });
  await userRef.update({
    role,
    updatedAt: FieldValue.serverTimestamp(),
  });

  const adminMarkerRef = db.collection("admins").doc(targetUid);
  if (role === "ADMIN") {
    await adminMarkerRef.set(
      {
        userId: targetUid,
        email: String(userSnap.data()?.email || ""),
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  } else {
    await adminMarkerRef.delete();
  }

  return { ok: true, targetUid, role };
});

/**
 * Signed Mobile Money provider webhook: only verified provider callbacks may mark
 * MOBILE_MONEY orders as PAID.
 */
export const mobileMoneyWebhook = onRequest(
  { secrets: [mobileMoneyWebhookSecret] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("Method Not Allowed");
      return;
    }

    const secret = mobileMoneyWebhookSecret.value().trim();
    const signature = String(req.headers["x-goprime-signature"] || "");
    if (!secret || !signature) {
      res.status(401).send("Unauthorized");
      return;
    }

    const expected = crypto
      .createHmac("sha256", secret)
      .update(JSON.stringify(req.body))
      .digest("hex");

    if (!crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected))) {
      logger.warn("Invalid Mobile Money webhook signature");
      res.status(403).send("Invalid signature");
      return;
    }

    const { orderId, status, transactionId } = req.body as {
      orderId?: string;
      status?: string;
      transactionId?: string;
    };

    if (!orderId || !status) {
      res.status(400).send("Missing orderId or status");
      return;
    }

    const paymentStatus = status === "SUCCESS" ? "PAID" : "FAILED";
    await db.collection("orders").doc(orderId).update({
      paymentStatus,
      paymentReference: transactionId || "",
      updatedAt: FieldValue.serverTimestamp(),
    });

    res.status(200).json({ verified: true, orderId, paymentStatus });
  }
);

/**
 * Admin-only callable to test official Meta WhatsApp Business Cloud API delivery once deployed.
 */
export const testWhatsappNotification = onCall(
  { secrets: [whatsappAccessToken, whatsappPhoneNumberId] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentification requise.");
    }
    await assertAdminCaller(
      request.auth.uid,
      request.auth.token.email,
      Boolean(request.auth.token.email_verified)
    );

    const { toPhone, recipientRole } = request.data as { toPhone?: string; recipientRole?: "ADMIN" | "CLIENT" };
    const roleLabel = recipientRole === "CLIENT" ? "CLIENT" : "ADMIN";
    const accessToken = whatsappAccessToken.value().trim();
    const phoneNumberId = whatsappPhoneNumberId.value().trim();
    if (!accessToken || !phoneNumberId) {
      throw new HttpsError(
        "failed-precondition",
        "Secrets WHATSAPP_ACCESS_TOKEN ou WHATSAPP_PHONE_NUMBER_ID manquants."
      );
    }

    const settingsSnap = await db.collection("settings").doc("whatsapp").get();
    const settings = settingsSnap.data() || {};
    const targetPhone = (toPhone || "").trim() ||
      (Array.isArray(settings.adminWhatsappPhones) ? String(settings.adminWhatsappPhones[0] || "") : "") ||
      DEFAULT_ADMIN_WHATSAPP_PHONE;

    if (normalizeWhatsAppPhone(targetPhone).length < 8) {
      throw new HttpsError("invalid-argument", "Aucun numéro WhatsApp valide configuré.");
    }

    const templateName = roleLabel === "CLIENT"
      ? String(settings.customerTemplateName || "goprime_order_confirmation")
      : String(settings.templateName || "goprime_new_order");

    const testPayload = buildWhatsAppOrderNotificationPayload({
      toPhone: targetPhone,
      templateName,
      languageCode: String(settings.languageCode || "fr"),
      orderNumber: "GP-TEST",
      customerLabel: roleLabel === "CLIENT" ? "Client Test GoPrime" : "Test Administrateur",
      totalLabel: "15 000 CDF (Espèces • Test)",
      transactionDetails: "Livraison Gombe (5 000 CDF) • 1x Pack Test GoPrime",
    });

    const result = await sendWhatsAppTemplateWithRetry({
      phoneNumberId,
      accessToken,
      payload: testPayload,
    });

    await db.collection("notificationLogs").add({
      orderId: "test",
      orderNumber: "GP-TEST",
      channel: "WHATSAPP_CLOUD_API",
      recipientRole: roleLabel,
      recipient: targetPhone,
      status: result.ok ? "SENT" : "FAILED",
      detail: result.ok
        ? `Test ${roleLabel} réussi (ID: ${result.messageId || "ok"})`
        : `HTTP ${result.statusCode}: ${result.errorDetail || "Erreur API"}`,
      createdAt: FieldValue.serverTimestamp(),
    });

    return result;
  }
);

/**
 * Cloud Function appelable dédiée à la vérification du rôle administrateur côté serveur.
 * - Un utilisateur standard ne peut vérifier que son propre UID.
 * - Seul un administrateur vérifié peut interroger le statut d'un autre `targetUid`.
 * - Si `requireAdmin === true` et que le compte n'est pas admin actif, rejette avec `permission-denied`.
 */
export const verifyAdminRole = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentification requise.");
  }

  const callerUid = request.auth.uid;
  const payload = (request.data || {}) as { targetUid?: string; requireAdmin?: boolean };
  const requestedTargetUid = typeof payload.targetUid === "string" ? payload.targetUid.trim() : "";
  const requireAdmin = Boolean(payload.requireAdmin);

  if (requestedTargetUid && requestedTargetUid !== callerUid) {
    await assertAdminCaller(
      callerUid,
      request.auth.token.email,
      Boolean(request.auth.token.email_verified)
    );
    const targetAuthUser = await getAuth().getUser(requestedTargetUid);
    const targetStatus = await verifyUserAdminStatus(
      requestedTargetUid,
      targetAuthUser.email,
      Boolean(targetAuthUser.emailVerified)
    );
    if (requireAdmin && !targetStatus.isAdmin) {
      throw new HttpsError(
        "permission-denied",
        "Accès refusé : le compte ciblé ne possède pas le rôle administrateur actif."
      );
    }
    return targetStatus;
  }

  const callerStatus = await verifyUserAdminStatus(
    callerUid,
    request.auth.token.email,
    Boolean(request.auth.token.email_verified)
  );

  if (requireAdmin && !callerStatus.isAdmin) {
    throw new HttpsError(
      "permission-denied",
      "Accès refusé : seul un administrateur GoPrime peut écrire dans la collection produits."
    );
  }

  return callerStatus;
});
