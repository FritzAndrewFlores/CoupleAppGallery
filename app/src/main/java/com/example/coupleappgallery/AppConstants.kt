package com.example.coupleappgallery

// --- DATA MODELS FOR MOODS & REACTIONS ---
data class MoodOption(val emoji: String, val label: String)
data class Reaction(val emoji: String, val label: String)

// --- 1. USER MOODS (10 Moods - shared by HomeScreen & ProfileScreen) ---
val Moods = listOf(
    MoodOption("😊", "Happy"),
    MoodOption("🥰", "Loved"),
    MoodOption("🥳", "Excited"),
    MoodOption("😎", "Cool"),
    MoodOption("😴", "Tired"),
    MoodOption("😤", "Angry"),
    MoodOption("🤯", "Stressed"),
    MoodOption("🤒", "Sick"),
    MoodOption("🥺", "Sad"),
    MoodOption("😐", "Neutral")
)

// --- 2. HOME SCREEN MOOD REACTIONS (33 Reactions for MoodCard) ---
val HomeReactions = listOf(
    Reaction("🫂", "Hug"),
    Reaction("💋", "Kiss"),
    Reaction("🧸", "Cuddle"),
    Reaction("❤️", "Love You"),
    Reaction("🫶", "Heart"),
    Reaction("😍", "Adore"),
    Reaction("💆", "Massage"),
    Reaction("👐", "Care"),
    Reaction("👂", "Listening"),
    Reaction("🥺", "Aww"),
    Reaction("💪", "You Got This"),
    Reaction("🛡️", "Safe"),
    Reaction("😂", "Laugh"),
    Reaction("🔥", "Hot"),
    Reaction("🎉", "Yay!"),
    Reaction("😎", "Cool"),
    Reaction("🥳", "Party"),
    Reaction("💃", "Dance"),
    Reaction("☕", "Coffee?"),
    Reaction("🍵", "Tea?"),
    Reaction("🍫", "Choco"),
    Reaction("🍦", "Treat"),
    Reaction("🍷", "Wine"),
    Reaction("🍕", "Food"),
    Reaction("📞", "Call Me"),
    Reaction("👀", "Look"),
    Reaction("👍", "Yes"),
    Reaction("👎", "No"),
    Reaction("🤫", "Shh"),
    Reaction("👏", "Clap"),
    Reaction("🌹", "Flowers"),
    Reaction("✨", "Magic"),
    Reaction("💤", "Rest")
)

// --- 3. PARTNER REQUEST OPTIONS (43 Options for RequestCard) ---
val RequestOptions = listOf(
    Reaction("💋", "Kiss"),
    Reaction("🫂", "Hug"),
    Reaction("🧸", "Cuddle"),
    Reaction("💆", "Massage"),
    Reaction("🤝", "Hold Hand"),
    Reaction("💆‍♂️", "Head Rub"),
    Reaction("🦷", "Bite"),
    Reaction("🛌", "Lay Down"),
    Reaction("📅", "Date Night"),
    Reaction("🍿", "Movie"),
    Reaction("🚶", "Walk"),
    Reaction("🚗", "Drive"),
    Reaction("🗣️", "Talk"),
    Reaction("🎮", "Game"),
    Reaction("✈️", "Trip"),
    Reaction("🏞️", "Park"),
    Reaction("🏖️", "Beach"),
    Reaction("☕", "Coffee"),
    Reaction("🧋", "Boba"),
    Reaction("🍕", "Pizza"),
    Reaction("🍔", "Burger"),
    Reaction("🍟", "Fries"),
    Reaction("🍣", "Sushi"),
    Reaction("🍦", "Ice Cream"),
    Reaction("🍩", "Donut"),
    Reaction("🍫", "Chocolate"),
    Reaction("🍹", "Drink"),
    Reaction("🍗", "Chicken"),
    Reaction("🥞", "Brunch"),
    Reaction("🥺", "Attention"),
    Reaction("👂", "Listen"),
    Reaction("🤫", "Quiet"),
    Reaction("😴", "Sleep"),
    Reaction("💤", "Nap"),
    Reaction("🚿", "Shower"),
    Reaction("🆘", "Help"),
    Reaction("💊", "Meds"),
    Reaction("💨", "Space"),
    Reaction("🛍️", "Shop"),
    Reaction("🎁", "Gift"),
    Reaction("🌹", "Flowers"),
    Reaction("💅", "Nails"),
    Reaction("💸", "Spoil Me")
)

// --- 4. CHAT MESSAGE REACTIONS (87 Emojis for ChatScreen) ---
val ReactionEmojis = listOf(
    // Favorites & Love
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
    "🥰", "😍", "😘", "😙", "😚", "💋", "💌", "💍", "🫂",

    // Happy & Fun
    "😂", "🤣", "🥹", "🤩", "😎", "🥳", "🎉", "🔥", "✨", "💃",
    "👍", "👎", "👏", "🙌", "🫶", "🤝", "🫡", "🫣", "🤔", "👀",

    // Sad, Angry & Shocked
    "😭", "😢", "🥺", "😤", "😡", "🤬", "🤯", "😱", "😓", "💀",
    "💩", "🤡", "👻", "👽", "🤖", "👿", "💤", "🤒", "🤕", "🤮",

    // Food & Random
    "🧸", "🌹", "🌻", "☀️", "🌧️", "🌈", "☕", "🥂", "🍻", "🍕",
    "🍔", "🍟", "🍦", "🍫", "🍩", "🍿", "🍆", "🍑", "🍒", "🍓",
    "💅", "👑", "💄", "👠", "🧢", "⚽", "🎮", "🚀", "💯", "✅"
)

// --- 5. JOURNAL MOODS (7 Emojis for GalleryScreen Journal) ---
val JournalMoods = listOf("😊", "🥰", "😂", "🤩", "😴", "😭", "😡")

// --- 6. JOURNAL ICONS (9 Icons for GalleryScreen Journal) ---
val JournalIcons = listOf("📔", "🌟", "✈️", "🍽️", "❤️", "🍷", "🏖️", "👶", "🏠")

// --- 7. FOLDER ICONS (52 Icons for HomeScreen FolderDialog) ---
val FolderIcons = listOf(
    "🌲", "⛺", "🏔️", "🛶", "🍂", "🍄", "🔥", "🚲", "🌻", "🌹", "🌸", "🌺", "🌴", "🌵",
    "🏠", "🏢", "🏰", "🎡", "🏥", "🏫", "🏛️", "⛪", "🌃", "🌉", "📚", "📖", "✏️", "🎓",
    "🎒", "💻", "🎨", "🍕", "🍔", "🍣", "🍦", "☕", "🍷", "🎂", "🍎", "✈️", "🚗", "🚂",
    "🗺️", "🏖️", "🚁", "❤️", "💍", "🎁", "🎵", "🎮", "🐾", "👶"
)

// --- 8. GOOGLE SIGN-IN WEB CLIENT ID ---
const val GOOGLE_WEB_CLIENT_ID = "534111994915-1gd6tc7iie7tba3mkovncou0r22fkp1o.apps.googleusercontent.com"

