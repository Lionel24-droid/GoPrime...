# GoPrime — Guide de déploiement & d'intégration (goprime-754cf)

## 1. Architecture actuelle dans l'application Android
- **Application Android native** (Kotlin + Jetpack Compose + Material 3).
- **Authentification** : Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption`) connecté à Firebase Authentication.
- **Base de données** : Cloud Firestore Enterprise (`ai-studio-android-goprime-f9c8093d-00cc-4e26-a4d1-18f0cd1cd23a`) sur le projet `goprime-754cf`, sécurisé par `firestore.rules`.
- **Catalogue** : Aucun produit fictif n'est pré-rempli. L'administrateur crée et gère les catégories, produits (prix en CDF, stocks) et zones de livraison depuis l'espace Administrateur.

## 2. Déploiement Firebase (Règles, Storage et Cloud Functions)

### Pré-requis
1. Installer Firebase CLI : `npm install -g firebase-tools`
2. Se connecter : `firebase login`
3. Sélectionner le projet : `firebase use goprime-754cf`
4. Activer **Firebase Storage** dans la console Firebase si vous souhaitez stocker des photos uploadées dans `goprime-754cf.firebasestorage.app`.

### Configuration des secrets serveur (obligatoire avant mise en production)
```bash
# WhatsApp Business Cloud API (Meta Graph API v21.0)
firebase functions:secrets:set WHATSAPP_ACCESS_TOKEN
firebase functions:secrets:set WHATSAPP_PHONE_NUMBER_ID

# Webhook Mobile Money (M-Pesa / Orange Money / Airtel Money agrégateur)
firebase functions:secrets:set MOBILE_MONEY_WEBHOOK_SECRET
```

### Déploiement
```bash
cd functions && npm install && npm run build && cd ..
firebase deploy --only firestore:rules,storage,functions
```

## 3. Configuration du modèle WhatsApp officiel (Meta Business Manager)
1. Créez un modèle approuvé dans WhatsApp Manager nommé `goprime_new_order` (langue `fr`) avec 4 variables dans le corps :
   - `{{1}}` : Numéro de commande (ex. `GP-104829`)
   - `{{2}}` : Nom du client
   - `{{3}}` : Montant total en CDF
   - `{{4}}` : Mode / Quartier de livraison
2. Dans l'application Android (**Espace Admin → Intégrations → WhatsApp**), ajoutez les numéros WhatsApp des administrateurs au format international (ex. `+243810000000`) et activez la bascule.
3. Placez une commande de test et vérifiez dans **Journaux de notifications** que le statut passe à `SENT` (et non `NOT_CONFIGURED`).

## 4. Paiement Mobile Money
- Les commandes Mobile Money sont créées avec `paymentStatus = "PENDING_VERIFICATION"`.
- Le passage à `PAID` ne peut être effectué côté client : il nécessite la vérification serveur via le fournisseur de paiement (`mobileMoneyWebhook` signé par HMAC-SHA256) ou une validation administrative explicite après contrôle du bordereau opérateur.
