const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_001";
const ADMIN_EMAIL = "alherilionel338@gmail.com";
const CLIENT_UID = "client_alice";
const OTHER_CLIENT_UID = "client_bob";
const DRIVER_UID = "driver_jean";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = () => new Date(Date.now() - 2000);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function adminDb() {
  return testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
}

function spoofedEmailAdminDb() {
  return testEnv
    .authenticatedContext("attacker_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
}

function clientDb(uid = CLIENT_UID) {
  return testEnv
    .authenticatedContext(uid, {
      email: `${uid}@example.com`,
      email_verified: true,
    })
    .firestore();
}

test("Unauthenticated users cannot read or write any GoPrime collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("products").where("active", "==", true).get());
  await assertFails(unauth.collection("categories").where("active", "==", true).get());
  await assertFails(unauth.collection("deliveryZones").where("active", "==", true).get());
  await assertFails(unauth.collection("orders").where("userId", "==", CLIENT_UID).get());
  await assertFails(
    unauth.collection("users").doc(CLIENT_UID).set({
      userId: CLIENT_UID,
      displayName: "Anon",
      email: "a@b.com",
      role: "CLIENT",
      active: true,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Email spoofing with unverified email cannot access admin actions", async () => {
  const spoofDb = spoofedEmailAdminDb();
  await assertFails(
    spoofDb.collection("categories").doc("cat_1").set({
      name: "Boissons",
      sortOrder: 1,
      active: true,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertFails(spoofDb.collection("settings").doc("whatsapp").get());
});

test("User profile registration, PII isolation, and role escalation prevention", async () => {
  const aliceDb = clientDb(CLIENT_UID);
  const bobDb = clientDb(OTHER_CLIENT_UID);
  const now = pastDate();

  // Client cannot self-assign ADMIN or DRIVER role
  await assertFails(
    aliceDb.collection("users").doc(CLIENT_UID).set({
      userId: CLIENT_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "ADMIN",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Client can register as CLIENT
  await assertSucceeds(
    aliceDb.collection("users").doc(CLIENT_UID).set({
      userId: CLIENT_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "CLIENT",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Client can update own contact fields
  await assertSucceeds(
    aliceDb.collection("users").doc(CLIENT_UID).update({
      phone: "+243810000111",
      defaultAddress: "Av. Lukusa 14",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Client cannot escalate own role
  await assertFails(
    aliceDb.collection("users").doc(CLIENT_UID).update({
      role: "ADMIN",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Client cannot self-assign admin marker
  await assertFails(
    aliceDb.collection("admins").doc(CLIENT_UID).set({
      userId: CLIENT_UID,
      email: "alice@example.com",
      createdAt: now,
    })
  );

  // Bob cannot read Alice's PII profile
  await assertFails(bobDb.collection("users").doc(CLIENT_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Admin manages catalog (categories, products, deliveryZones) and client queries active items", async () => {
  const adm = adminDb();
  const alice = clientDb(CLIENT_UID);
  const now = pastDate();

  // Admin creates category
  await assertSucceeds(
    adm.collection("categories").doc("cat_food").set({
      name: "Alimentation",
      description: "Produits frais",
      sortOrder: 1,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Product requires existing categoryId
  await assertFails(
    adm.collection("products").doc("prod_orphan").set({
      name: "Produit Orphelin",
      description: "Test",
      categoryId: "missing_cat",
      categoryName: "None",
      priceCdf: 5000,
      stock: 10,
      featured: false,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid active & inactive products
  await assertSucceeds(
    adm.collection("products").doc("prod_rice").set({
      name: "Riz Basmati 5kg",
      description: "Sac 5kg",
      categoryId: "cat_food",
      categoryName: "Alimentation",
      priceCdf: 28000,
      stock: 20,
      featured: true,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adm.collection("products").doc("prod_draft").set({
      name: "Brouillon",
      description: "Non publié",
      categoryId: "cat_food",
      categoryName: "Alimentation",
      priceCdf: 10000,
      stock: 5,
      featured: false,
      active: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Client filtered query succeeds; unfiltered list & draft get fail
  await assertSucceeds(alice.collection("products").where("active", "==", true).get());
  await assertFails(alice.collection("products").get());
  await assertFails(alice.collection("products").doc("prod_draft").get());

  // Client cannot create, update, or delete products
  await assertFails(
    alice.collection("products").doc("prod_hack").set({
      name: "Hack",
      description: "Hack",
      categoryId: "cat_food",
      categoryName: "Alimentation",
      priceCdf: 100,
      stock: 10,
      featured: false,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    alice.collection("products").doc("prod_rice").update({
      priceCdf: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(alice.collection("products").doc("prod_rice").delete());

  // Driver cannot create, update, or delete products
  const driver = clientDb(DRIVER_UID);
  await assertSucceeds(
    adm.collection("users").doc(DRIVER_UID).set({
      userId: DRIVER_UID,
      displayName: "Jean",
      email: "jean@example.com",
      role: "DRIVER",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    driver.collection("products").doc("prod_driver_hack").set({
      name: "DriverHack",
      description: "Hack",
      categoryId: "cat_food",
      categoryName: "Alimentation",
      priceCdf: 100,
      stock: 10,
      featured: false,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    driver.collection("products").doc("prod_rice").update({
      stock: 0,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(driver.collection("products").doc("prod_rice").delete());

  // Promoted active admin CAN create, update, and delete products
  const promotedUid = "promoted_admin_1";
  const promotedAdmin = clientDb(promotedUid);
  await assertSucceeds(
    adm.collection("users").doc(promotedUid).set({
      userId: promotedUid,
      displayName: "Admin Adjoint",
      email: "promoted@example.com",
      role: "ADMIN",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adm.collection("admins").doc(promotedUid).set({
      userId: promotedUid,
      email: "promoted@example.com",
      createdAt: now,
    })
  );
  await assertSucceeds(
    promotedAdmin.collection("products").doc("prod_promoted").set({
      name: "Huile Palme 5L",
      description: "Bidon 5L",
      categoryId: "cat_food",
      categoryName: "Alimentation",
      priceCdf: 19500,
      stock: 12,
      featured: false,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    promotedAdmin.collection("products").doc("prod_promoted").update({
      priceCdf: 20500,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Suspended admin loses write access to products immediately
  await assertSucceeds(
    adm.collection("users").doc(promotedUid).update({
      active: false,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    promotedAdmin.collection("products").doc("prod_promoted").update({
      priceCdf: 1,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(promotedAdmin.collection("products").doc("prod_promoted").delete());

  // Re-activate promoted admin -> delete succeeds
  await assertSucceeds(
    adm.collection("users").doc(promotedUid).update({
      active: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertSucceeds(promotedAdmin.collection("products").doc("prod_promoted").delete());
});

test("Cart operations are strictly isolated to owner", async () => {
  const alice = clientDb(CLIENT_UID);
  const bob = clientDb(OTHER_CLIENT_UID);

  await assertSucceeds(
    alice.collection("users").doc(CLIENT_UID).collection("cart").doc("prod_rice").set({
      productId: "prod_rice",
      productName: "Riz Basmati 5kg",
      unitPriceCdf: 28000,
      quantity: 2,
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(alice.collection("users").doc(CLIENT_UID).collection("cart").get());
  await assertFails(bob.collection("users").doc(CLIENT_UID).collection("cart").get());
});

test("Order creation enforces zone fees, forbids client PAID status, and enforces workflow", async () => {
  const adm = adminDb();
  const alice = clientDb(CLIENT_UID);
  const bob = clientDb(OTHER_CLIENT_UID);
  const driver = clientDb(DRIVER_UID);
  const now = pastDate();

  // Admin creates delivery zone Gombe = 5000 CDF
  await assertSucceeds(
    adm.collection("deliveryZones").doc("zone_gombe").set({
      name: "Gombe",
      commune: "Gombe",
      feeCdf: 5000,
      estimatedMinutes: 35,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Tampered delivery fee (0 instead of 5000) must fail
  await assertFails(
    alice.collection("orders").doc("ord_tamper").set({
      orderNumber: "GP-100001",
      userId: CLIENT_UID,
      customerName: "Alice",
      customerPhone: "+243810000111",
      fulfillmentMode: "DELIVERY",
      districtId: "zone_gombe",
      districtName: "Gombe",
      deliveryAddress: "Av. des Aviateurs 12",
      items: [{ productId: "prod_rice", productName: "Riz", unitPriceCdf: 28000, quantity: 1 }],
      subtotalCdf: 28000,
      deliveryFeeCdf: 0,
      totalCdf: 28000,
      paymentMethod: "CASH",
      paymentStatus: "PENDING",
      status: "PENDING",
      whatsappNotificationStatus: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Client cannot mark Mobile Money order as PAID on create
  await assertFails(
    alice.collection("orders").doc("ord_paid_hack").set({
      orderNumber: "GP-100002",
      userId: CLIENT_UID,
      customerName: "Alice",
      customerPhone: "+243810000111",
      fulfillmentMode: "DELIVERY",
      districtId: "zone_gombe",
      districtName: "Gombe",
      deliveryAddress: "Av. des Aviateurs 12",
      items: [{ productId: "prod_rice", productName: "Riz", unitPriceCdf: 28000, quantity: 1 }],
      subtotalCdf: 28000,
      deliveryFeeCdf: 5000,
      totalCdf: 33000,
      paymentMethod: "MOBILE_MONEY",
      paymentProvider: "M-Pesa",
      paymentPhone: "+243810000111",
      paymentStatus: "PAID",
      status: "PENDING",
      whatsappNotificationStatus: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid order creation succeeds
  await assertSucceeds(
    alice.collection("orders").doc("ord_valid").set({
      orderNumber: "GP-100003",
      userId: CLIENT_UID,
      customerName: "Alice",
      customerPhone: "+243810000111",
      fulfillmentMode: "DELIVERY",
      districtId: "zone_gombe",
      districtName: "Gombe",
      deliveryAddress: "Av. des Aviateurs 12",
      items: [{ productId: "prod_rice", productName: "Riz", unitPriceCdf: 28000, quantity: 1 }],
      subtotalCdf: 28000,
      deliveryFeeCdf: 5000,
      totalCdf: 33000,
      paymentMethod: "CASH",
      paymentStatus: "PENDING",
      status: "PENDING",
      whatsappNotificationStatus: "PENDING",
      customerWhatsappStatus: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read Alice's order, and Alice cannot do unfiltered list
  await assertFails(bob.collection("orders").doc("ord_valid").get());
  await assertFails(alice.collection("orders").get());
  await assertSucceeds(alice.collection("orders").where("userId", "==", CLIENT_UID).get());

  // Admin accepts & assigns driver
  await assertSucceeds(
    adm.collection("orders").doc("ord_valid").update({
      status: "ACCEPTED",
      assignedDriverId: DRIVER_UID,
      assignedDriverName: "Jean Livreur",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Client can no longer cancel once ACCEPTED
  await assertFails(
    alice.collection("orders").doc("ord_valid").update({
      status: "CANCELLED",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Driver queries assigned orders & advances status ACCEPTED -> OUT_FOR_DELIVERY -> COMPLETED (CASH PAID)
  await assertSucceeds(driver.collection("orders").where("assignedDriverId", "==", DRIVER_UID).get());
  await assertSucceeds(
    driver.collection("orders").doc("ord_valid").update({
      status: "OUT_FOR_DELIVERY",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertSucceeds(
    driver.collection("orders").doc("ord_valid").update({
      status: "COMPLETED",
      paymentStatus: "PAID",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Driver cannot mutate terminal COMPLETED order
  await assertFails(
    driver.collection("orders").doc("ord_valid").update({
      status: "OUT_FOR_DELIVERY",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Settings and notificationLogs are strictly restricted to Admin", async () => {
  const adm = adminDb();
  const alice = clientDb(CLIENT_UID);
  const now = pastDate();

  await assertSucceeds(
    adm.collection("settings").doc("whatsapp").set({
      enabled: true,
      adminWhatsappPhones: ["+243810000999"],
      notifyCustomerOnOrder: true,
      templateName: "goprime_new_order",
      customerTemplateName: "goprime_order_confirmation",
      languageCode: "fr",
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adm.collection("notificationLogs").doc("log_1").set({
      orderId: "ord_valid",
      orderNumber: "GP-100003",
      channel: "WHATSAPP_CLOUD_API",
      recipientRole: "CLIENT",
      recipient: "+243810000111",
      status: "SENT",
      detail: "Confirmation envoyée",
      createdAt: now,
    })
  );

  await assertFails(alice.collection("settings").doc("whatsapp").get());
  await assertFails(alice.collection("notificationLogs").get());
  await assertSucceeds(adm.collection("notificationLogs").get());
});

test("Customer reviews are allowed only for owner's COMPLETED orders and readable by signed-in users", async () => {
  const adm = adminDb();
  const alice = clientDb(CLIENT_UID);
  const bob = clientDb(OTHER_CLIENT_UID);
  const now = pastDate();

  await assertSucceeds(
    adm.collection("deliveryZones").doc("zone_gombe").set({
      name: "Gombe",
      commune: "Gombe",
      feeCdf: 5000,
      estimatedMinutes: 35,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create Alice's order (initially PENDING)
  await assertSucceeds(
    alice.collection("orders").doc("ord_review_1").set({
      orderNumber: "GP-200001",
      userId: CLIENT_UID,
      customerName: "Alice",
      customerPhone: "+243810000111",
      fulfillmentMode: "DELIVERY",
      districtId: "zone_gombe",
      districtName: "Gombe",
      deliveryAddress: "Av. des Aviateurs 12",
      items: [{ productId: "prod_rice", productName: "Riz", unitPriceCdf: 28000, quantity: 1 }],
      subtotalCdf: 28000,
      deliveryFeeCdf: 5000,
      totalCdf: 33000,
      paymentMethod: "CASH",
      paymentStatus: "PENDING",
      status: "PENDING",
      whatsappNotificationStatus: "PENDING",
      customerWhatsappStatus: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reviewing PENDING order must fail
  await assertFails(
    alice.collection("reviews").doc("ord_review_1").set({
      orderId: "ord_review_1",
      orderNumber: "GP-200001",
      userId: CLIENT_UID,
      customerName: "Alice",
      productIds: ["prod_rice"],
      productRating: 5,
      deliveryRating: 5,
      comment: "Super rapide",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin marks order as COMPLETED
  await assertSucceeds(
    adm.collection("orders").doc("ord_review_1").update({
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot submit review for Alice's order
  await assertFails(
    bob.collection("reviews").doc("ord_review_1").set({
      orderId: "ord_review_1",
      orderNumber: "GP-200001",
      userId: OTHER_CLIENT_UID,
      customerName: "Bob",
      productIds: ["prod_rice"],
      productRating: 1,
      deliveryRating: 1,
      comment: "Fake review",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid out-of-range rating (6) must fail
  await assertFails(
    alice.collection("reviews").doc("ord_review_1").set({
      orderId: "ord_review_1",
      orderNumber: "GP-200001",
      userId: CLIENT_UID,
      customerName: "Alice",
      productIds: ["prod_rice"],
      productRating: 6,
      deliveryRating: 5,
      comment: "Invalid",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice can review her COMPLETED order
  await assertSucceeds(
    alice.collection("reviews").doc("ord_review_1").set({
      orderId: "ord_review_1",
      orderNumber: "GP-200001",
      userId: CLIENT_UID,
      customerName: "Alice",
      productIds: ["prod_rice"],
      productRating: 5,
      deliveryRating: 4,
      comment: "Riz de très bonne qualité, livreur ponctuel.",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice can update her own review
  await assertSucceeds(
    alice.collection("reviews").doc("ord_review_1").update({
      productRating: 5,
      deliveryRating: 5,
      comment: "Excellent service !",
      customerName: "Alice",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Bob can read reviews
  await assertSucceeds(bob.collection("reviews").get());
});

