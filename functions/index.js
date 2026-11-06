const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendPushNotification = onDocumentCreated("notifications/{docId}", async (event) => {
    const snap = event.data;
    if (!snap) {
        return null;
    }

    const notificationData = snap.data();
    const receiverId = notificationData.to;
    const rawTitle = notificationData.title || "New Update";
    const rawBody = notificationData.body || "Open the app to see!";
    const title = rawTitle.length > 120 ? rawTitle.slice(0, 117) + "..." : rawTitle;
    const body = rawBody.length > 500 ? rawBody.slice(0, 497) + "..." : rawBody;

    if (!receiverId) {
        console.log("No receiverId found, aborting.");
        return null;
    }

    const userRef = admin.firestore().collection("users").doc(receiverId);

    try {
        const userDoc = await userRef.get();

        if (!userDoc.exists) {
            console.log(`User ${receiverId} does not exist.`);
            return null;
        }

        const fcmToken = userDoc.data().fcmToken;

        if (!fcmToken) {
            console.log(`User ${receiverId} does not have an FCM token.`);
            return null;
        }

        const lowerTitle = title.toLowerCase();
        const route = lowerTitle.includes("message") || title.includes("💌") || lowerTitle.includes("chat") || lowerTitle.includes("new reaction")
            ? "chat"
            : "home";

        const payload = {
            notification: {
                title: title,
                body: body,
            },
            android: {
                priority: "high",
                notification: {
                    channelId: "COUPLE_APP_CHANNEL",
                    icon: "ic_stat_heart",
                },
            },
            data: {
                docId: event.params.docId || "",
                title: title,
                body: body,
                route: route,
            },
            token: fcmToken,
        };

        const response = await admin.messaging().send(payload);
        console.log("Successfully sent push notification:", response);
        return response;
    } catch (error) {
        if (
            error &&
            (error.code === "messaging/registration-token-not-registered" ||
                error.code === "messaging/invalid-registration-token")
        ) {
            console.log(`Pruning stale FCM token for user ${receiverId}`);
            await userRef.update({ fcmToken: admin.firestore.FieldValue.delete() }).catch(() => {});
        } else {
            console.error("Error sending push notification:", error);
        }
        return null;
    }
});
