const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendPushNotification = onDocumentCreated("notifications/{docId}", async (event) => {
    // Get the newly created document
    const snap = event.data;
    if (!snap) {
        return null;
    }

    const notificationData = snap.data();
    const receiverId = notificationData.to;
    const title = notificationData.title || "New Update";
    const body = notificationData.body || "Open the app to see!";

    // 1. Check if we have a receiver
    if (!receiverId) {
        console.log("No receiverId found, aborting.");
        return null;
    }

    try {
        // 2. Look up the receiver's FCM Token in the 'users' collection
        const userDoc = await admin.firestore().collection("users").doc(receiverId).get();

        if (!userDoc.exists) {
            console.log(`User ${receiverId} does not exist.`);
            return null;
        }

        const fcmToken = userDoc.data().fcmToken;

        if (!fcmToken) {
            console.log(`User ${receiverId} does not have an FCM token.`);
            return null;
        }

        // 3. Construct the Push Notification Payload
        const payload = {
            notification: {
                title: title,
                body: body,
            },
            token: fcmToken
        };

        // 4. Send the Notification via Firebase Cloud Messaging
        const response = await admin.messaging().send(payload);
        console.log("Successfully sent push notification:", response);
        return response;

    } catch (error) {
        console.error("Error sending push notification:", error);
        return null;
    }
});