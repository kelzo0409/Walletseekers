package com.hackerai.walletseeker.data.scanner

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.gson.JsonParser
import com.hackerai.walletseeker.data.model.*
import com.hackerai.walletseeker.domain.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class FileScanner(
    private val context: Context,
    private val config: AppConfig
) {
    data class ScanProgress(
        val wallets: List<WalletModel>,
        val totalFilesScanned: Int,
        val directoriesSearched: Int,
        val currentFile: String = "",
        val errors: List<String> = emptyList(),
        val isComplete: Boolean = false
    )

    private val bip39WordList: Set<String> by lazy {
        val words = setOf(
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse",
            "access", "accident", "account", "accuse", "achieve", "acid", "acoustic", "acquire", "across", "act",
            "action", "actor", "actress", "actual", "adapt", "add", "addict", "address", "adjust", "admit",
            "adult", "advance", "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
            "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album", "alcohol", "alert",
            "alien", "all", "alley", "allow", "almost", "alone", "alpha", "already", "also", "alter",
            "always", "amateur", "amazing", "among", "amount", "amused", "analyst", "anchor", "ancient", "anger",
            "angle", "angry", "animal", "ankle", "announce", "annual", "another", "answer", "antenna", "antique",
            "anxiety", "any", "apart", "apology", "appear", "apple", "approve", "april", "arch", "arctic",
            "area", "arena", "argue", "arm", "armed", "armor", "army", "around", "arrange", "arrest",
            "arrive", "arrow", "art", "artefact", "artist", "artwork", "ask", "aspect", "assault", "asset",
            "assist", "assume", "asthma", "athlete", "atom", "attack", "attend", "attitude", "attract", "auction",
            "audit", "august", "aunt", "author", "auto", "autumn", "average", "avocado", "avoid", "awake",
            "aware", "away", "awesome", "awful", "awkward", "axis", "baby", "bachelor", "bacon", "badge",
            "bag", "balance", "balcony", "ball", "bamboo", "banana", "banner", "bar", "barely", "bargain",
            "barrel", "base", "basic", "basket", "battle", "beach", "bean", "beauty", "because", "become",
            "beef", "before", "begin", "behave", "behind", "believe", "below", "belt", "bench", "benefit",
            "best", "betray", "better", "between", "beyond", "bicycle", "bid", "bike", "bind", "biology",
            "bird", "birth", "bitter", "black", "blade", "blame", "blanket", "blast", "bleak", "bless",
            "blind", "blood", "blossom", "blouse", "blue", "blur", "blush", "board", "boat", "body",
            "boil", "bomb", "bone", "bonus", "book", "boost", "border", "boring", "borrow", "boss",
            "bottom", "bounce", "box", "boy", "bracket", "brain", "brand", "brass", "brave", "bread",
            "breeze", "brick", "bridge", "brief", "bright", "bring", "brisk", "broccoli", "broken", "bronze",
            "broom", "brother", "brown", "brush", "bubble", "buddy", "budget", "buffalo", "build", "bulb",
            "bulk", "bullet", "bundle", "bunker", "burden", "burger", "burst", "bus", "business", "busy",
            "butter", "buyer", "buzz", "cabbage", "cabin", "cable", "cactus", "cage", "cake", "call",
            "calm", "camera", "camp", "can", "canal", "cancel", "candy", "cannon", "canoe", "canvas",
            "canyon", "capable", "capital", "captain", "car", "carbon", "card", "cargo", "carpet", "carry",
            "cart", "case", "cash", "casino", "castle", "casual", "cat", "catalog", "catch", "category",
            "cattle", "caught", "cause", "caution", "cave", "ceiling", "celery", "cement", "census", "century",
            "cereal", "certain", "chair", "chalk", "champion", "change", "chaos", "chapter", "charge", "chase",
            "chat", "cheap", "check", "cheese", "chef", "cherry", "chest", "chicken", "chief", "child",
            "chimney", "choice", "choose", "chronic", "chuckle", "chunk", "churn", "cigar", "cinnamon", "circle",
            "citizen", "city", "civil", "claim", "clap", "clarify", "claw", "clay", "clean", "clerk",
            "clever", "click", "client", "cliff", "climb", "clinic", "clip", "clock", "clog", "close",
            "cloth", "cloud", "clown", "club", "clump", "cluster", "clutch", "coach", "coast", "coconut",
            "code", "coffee", "coil", "coin", "collect", "color", "column", "combine", "come", "comfort",
            "comic", "common", "company", "concert", "conduct", "confirm", "congress", "connect", "consider", "control",
            "convince", "cook", "cool", "copper", "copy", "coral", "core", "corn", "correct", "cost",
            "cotton", "couch", "country", "couple", "course", "cousin", "cover", "coyote", "crack", "cradle",
            "craft", "cram", "crane", "crash", "crater", "crawl", "crazy", "cream", "credit", "creek",
            "crew", "cricket", "crime", "crisp", "critic", "crop", "cross", "crouch", "crowd", "crucial",
            "cruel", "cruise", "crumble", "crunch", "crush", "cry", "crystal", "cube", "culture", "cup",
            "cupboard", "curious", "current", "curtain", "curve", "cushion", "custom", "cute", "cycle", "dad",
            "damage", "damp", "dance", "danger", "daring", "dash", "daughter", "dawn", "day", "deal",
            "debate", "debris", "decade", "december", "decide", "decline", "decorate", "decrease", "deer", "defense",
            "define", "defy", "degree", "delay", "deliver", "demand", "demise", "denial", "dentist", "deny",
            "depart", "depend", "deposit", "depth", "deputy", "derive", "describe", "desert", "design", "desk",
            "despair", "destroy", "detail", "detect", "develop", "device", "devote", "diagram", "dial", "diamond",
            "diary", "dice", "diesel", "diet", "differ", "digital", "dignity", "dilemma", "dinner", "dinosaur",
            "direct", "dirt", "disagree", "discover", "disease", "dish", "dismiss", "disorder", "display", "distance",
            "divert", "divide", "divorce", "dizzy", "doctor", "document", "dog", "doll", "dolphin", "domain",
            "donate", "donkey", "donor", "door", "dose", "double", "dove", "draft", "dragon", "drama",
            "drastic", "draw", "dream", "dress", "drift", "drill", "drink", "drip", "drive", "drop",
            "drum", "dry", "duck", "dumb", "dune", "during", "dust", "dutch", "duty", "dwarf",
            "dynamic", "eager", "eagle", "early", "earn", "earth", "easily", "east", "easy", "echo",
            "ecology", "economy", "edge", "edit", "educate", "effort", "egg", "eight", "either", "elbow",
            "elder", "electric", "elegant", "element", "elephant", "elevator", "elite", "else", "embark", "embody",
            "embrace", "emerge", "emotion", "employ", "empower", "empty", "enable", "enact", "end", "endless",
            "endorse", "enemy", "energy", "enforce", "engage", "engine", "enhance", "enjoy", "enlist", "enough",
            "enrich", "enroll", "ensure", "enter", "entire", "entry", "envelope", "episode", "equal", "equip",
            "era", "erase", "erode", "erosion", "error", "erupt", "escape", "essay", "essence", "estate",
            "eternal", "ethics", "evidence", "evil", "evoke", "evolve", "exact", "example", "excess", "exchange",
            "excite", "exclude", "excuse", "execute", "exercise", "exhaust", "exhibit", "exile", "exist", "exit",
            "exotic", "expand", "expect", "expire", "explain", "expose", "express", "extend", "extra", "eye",
            "eyebrow", "fabric", "face", "faculty", "fade", "faint", "faith", "fall", "false", "fame",
            "family", "famous", "fan", "fancy", "fantasy", "farm", "fashion", "fat", "fatal", "father",
            "fatigue", "fault", "favorite", "feature", "february", "federal", "fee", "feed", "feel", "female",
            "fence", "festival", "fetch", "fever", "few", "fiber", "fiction", "field", "figure", "file",
            "film", "filter", "final", "find", "fine", "finger", "finish", "fire", "firm", "first",
            "fiscal", "fish", "fit", "fitness", "fix", "flag", "flame", "flash", "flat", "flavor",
            "flee", "flight", "flip", "float", "flock", "floor", "flower", "fluid", "flush", "fly",
            "foam", "focus", "fog", "foil", "fold", "follow", "food", "foot", "force", "foreign",
            "forest", "forget", "fork", "fortune", "forum", "forward", "fossil", "foster", "found", "fox",
            "fragile", "frame", "frequent", "fresh", "friend", "fringe", "frog", "front", "frost", "frown",
            "frozen", "fruit", "fuel", "fun", "funny", "furnace", "fury", "future", "gadget", "gain",
            "galaxy", "gallery", "game", "gap", "garage", "garbage", "garden", "garlic", "garment", "gas",
            "gasp", "gate", "gather", "gauge", "gaze", "general", "genius", "genre", "gentle", "genuine",
            "gesture", "ghost", "giant", "gift", "giggle", "ginger", "giraffe", "girl", "give", "glad",
            "glance", "glare", "glass", "glide", "glimpse", "globe", "gloom", "glory", "glove", "glow",
            "glue", "goat", "goddess", "gold", "good", "goose", "gorilla", "gospel", "gossip", "govern",
            "gown", "grab", "grace", "grain", "grant", "grape", "grass", "gravity", "great", "green",
            "grid", "grief", "grit", "grocery", "group", "grow", "grunt", "guard", "guess", "guide",
            "guilt", "guitar", "gun", "gym", "habit", "hair", "half", "hammer", "hamster", "hand",
            "happy", "harbor", "hard", "harsh", "harvest", "hat", "have", "hawk", "hazard", "head",
            "health", "heart", "heavy", "hedgehog", "height", "hello", "helmet", "help", "hen", "hero",
            "hidden", "high", "hill", "hint", "hip", "hire", "history", "hobby", "hockey", "hold",
            "hole", "holiday", "hollow", "home", "honey", "hood", "hope", "horn", "horror", "horse",
            "hospital", "host", "hotel", "hour", "hover", "hub", "huge", "human", "humble", "humor",
            "hundred", "hungry", "hunt", "hurdle", "hurry", "hurt", "husband", "hybrid", "ice", "icon",
            "idea", "identify", "idle", "ignore", "ill", "illegal", "illness", "image", "imitate", "immense",
            "immune", "impact", "impose", "improve", "impulse", "inch", "include", "income", "increase", "index",
            "indicate", "indoor", "industry", "infant", "inflict", "inform", "inhale", "inherit", "initial", "inject",
            "injury", "inmate", "inner", "innocent", "input", "inquiry", "insane", "insect", "inside", "inspire",
            "install", "intact", "interest", "into", "invest", "invite", "involve", "iron", "island", "isolate",
            "issue", "item", "ivory", "jacket", "jaguar", "jar", "jazz", "jealous", "jeans", "jelly",
            "jewel", "job", "join", "joke", "journey", "joy", "judge", "juice", "jump", "jungle",
            "junior", "junk", "just", "kangaroo", "keen", "keep", "ketchup", "key", "kick", "kid",
            "kidney", "kind", "kingdom", "kiss", "kit", "kitchen", "kite", "kitten", "kiwi", "knee",
            "knife", "knock", "know", "lab", "label", "labor", "ladder", "lady", "lake", "lamp",
            "language", "laptop", "large", "later", "latin", "laugh", "laundry", "lava", "law", "lawn",
            "lawsuit", "layer", "lazy", "leader", "leaf", "learn", "leave", "lecture", "left", "leg",
            "legal", "legend", "leisure", "lemon", "lend", "length", "lens", "leopard", "lesson", "letter",
            "level", "liar", "liberty", "library", "license", "life", "lift", "light", "like", "limb",
            "limit", "link", "lion", "liquid", "list", "little", "live", "lizard", "load", "loan",
            "lobster", "local", "lock", "logic", "lonely", "long", "loop", "lottery", "loud", "lounge",
            "love", "loyal", "lucky", "luggage", "lumber", "lunar", "lunch", "luxury", "lyrics", "machine",
            "mad", "magic", "magnet", "maid", "mail", "main", "major", "make", "mammal", "man",
            "manage", "mandate", "mango", "mansion", "manual", "maple", "marble", "march", "margin", "marine",
            "market", "marriage", "mask", "mass", "master", "match", "material", "math", "matrix", "matter",
            "maximum", "maze", "meadow", "mean", "measure", "meat", "mechanic", "medal", "media", "melody",
            "melt", "member", "memory", "mention", "menu", "mercy", "merge", "merit", "merry", "mesh",
            "message", "metal", "method", "middle", "midnight", "milk", "million", "mimic", "mind", "minimum",
            "minor", "minute", "miracle", "mirror", "misery", "miss", "mistake", "mix", "mixed", "mixture",
            "mobile", "model", "modify", "mom", "moment", "monitor", "monkey", "monster", "month", "moon",
            "moral", "more", "morning", "mosquito", "mother", "motion", "motor", "mountain", "mouse", "move",
            "movie", "much", "muffin", "mule", "multiply", "muscle", "museum", "mushroom", "music", "must",
            "mutual", "myself", "mystery", "myth", "naive", "name", "napkin", "narrow", "nasty", "nation",
            "nature", "near", "neck", "need", "negative", "neglect", "neither", "nephew", "nerve", "nest",
            "net", "network", "neutral", "never", "news", "next", "nice", "night", "noble", "noise",
            "nominee", "noodle", "normal", "north", "nose", "notable", "note", "nothing", "notice", "novel",
            "now", "nuclear", "number", "nurse", "nut", "oak", "obey", "object", "oblige", "obscure",
            "observe", "obtain", "obvious", "occur", "ocean", "october", "odor", "off", "offer", "office",
            "often", "oil", "okay", "old", "olive", "olympic", "omit", "once", "one", "onion",
            "online", "only", "open", "opera", "opinion", "oppose", "option", "orange", "orbit", "orchard",
            "order", "ordinary", "organ", "orient", "original", "orphan", "ostrich", "other", "outdoor", "outer",
            "output", "outside", "oval", "oven", "over", "own", "owner", "oxygen", "oyster", "ozone",
            "pact", "paddle", "page", "pair", "palace", "palm", "panda", "panel", "panic", "panther",
            "paper", "parade", "parent", "park", "parrot", "party", "pass", "patch", "path", "patient",
            "patrol", "pattern", "pause", "pave", "payment", "peace", "peanut", "pear", "peasant", "pelican",
            "pen", "penalty", "pencil", "people", "pepper", "perfect", "permit", "person", "pet", "phone",
            "photo", "phrase", "physical", "piano", "picnic", "picture", "piece", "pig", "pigeon", "pill",
            "pilot", "pink", "pioneer", "pipe", "pistol", "pitch", "pizza", "place", "planet", "plastic",
            "plate", "play", "please", "pledge", "pluck", "plug", "plunge", "poem", "poet", "point",
            "polar", "pole", "police", "pond", "pony", "pool", "popular", "portion", "position", "possible",
            "post", "potato", "pottery", "poverty", "powder", "power", "practice", "praise", "predict", "prefer",
            "prepare", "present", "pretty", "prevent", "price", "pride", "primary", "print", "priority", "prison",
            "private", "prize", "problem", "process", "produce", "profit", "program", "project", "promote", "proof",
            "property", "prosper", "protect", "proud", "provide", "public", "pudding", "pull", "pulp", "pulse",
            "pumpkin", "punch", "pupil", "puppy", "purchase", "purity", "purpose", "purse", "push", "put",
            "puzzle", "pyramid", "quality", "quantum", "quarter", "question", "quick", "quit", "quiz", "quote",
            "rabbit", "raccoon", "race", "rack", "radar", "radio", "rail", "rain", "raise", "rally",
            "ramp", "ranch", "random", "range", "rapid", "rare", "rate", "rather", "raven", "raw",
            "razor", "ready", "real", "reason", "rebel", "rebuild", "recall", "receive", "recipe", "record",
            "recycle", "reduce", "reflect", "reform", "refuse", "region", "regret", "regular", "reject", "relax",
            "release", "relief", "rely", "remain", "remember", "remind", "remove", "render", "renew", "rent",
            "reopen", "repair", "repeat", "replace", "report", "require", "rescue", "resemble", "resist", "resource",
            "response", "result", "retire", "retreat", "return", "reunion", "reveal", "review", "reward", "rhythm",
            "rib", "ribbon", "rice", "rich", "ride", "ridge", "rifle", "right", "rigid", "ring",
            "riot", "ripple", "risk", "ritual", "rival", "river", "road", "roast", "robot", "robust",
            "rocket", "romance", "roof", "rookie", "room", "rose", "rotate", "rough", "round", "route",
            "royal", "rubber", "rude", "rug", "rule", "run", "runway", "rural", "sad", "saddle",
            "sadness", "safe", "sail", "salad", "salmon", "salon", "salt", "salute", "same", "sample",
            "sand", "satisfy", "satoshi", "sauce", "sausage", "save", "say", "scale", "scan", "scare",
            "scatter", "scene", "scheme", "school", "science", "scissors", "scorpion", "scout", "scrap", "screen",
            "script", "scrub", "sea", "search", "season", "seat", "second", "secret", "section", "security",
            "seed", "seek", "segment", "select", "sell", "seminar", "senior", "sense", "sentence", "series",
            "service", "session", "settle", "setup", "seven", "shadow", "shaft", "shallow", "share", "shed",
            "shell", "sheriff", "shield", "shift", "shine", "ship", "shiver", "shock", "shoe", "shoot",
            "shop", "short", "shoulder", "shove", "shrimp", "shrug", "shuffle", "shy", "sibling", "sick",
            "side", "siege", "sight", "sign", "silent", "silk", "silly", "silver", "similar", "simple",
            "since", "sing", "siren", "sister", "situate", "six", "size", "skate", "sketch", "ski",
            "skill", "skin", "skirt", "skull", "slab", "slam", "sleep", "slender", "slice", "slide",
            "slight", "slim", "slogan", "slot", "slow", "slush", "small", "smart", "smile", "smoke",
            "smooth", "snack", "snake", "snap", "sniff", "snow", "soap", "soccer", "social", "sock",
            "soda", "soft", "solar", "soldier", "solid", "solution", "solve", "someone", "song", "soon",
            "sorry", "sort", "soul", "sound", "soup", "source", "south", "space", "spare", "spatial",
            "spawn", "speak", "special", "speed", "spell", "spend", "sphere", "spice", "spider", "spike",
            "spin", "spirit", "split", "spoil", "sponsor", "spoon", "sport", "spot", "spray", "spread",
            "spring", "spy", "square", "squeeze", "squirrel", "stable", "stadium", "staff", "stage", "stairs",
            "stamp", "stand", "start", "state", "stay", "steak", "steel", "step", "stereo", "stick",
            "still", "sting", "stock", "stomach", "stone", "stool", "story", "stove", "strategy", "street",
            "strike", "strong", "struggle", "student", "stuff", "stumble", "style", "subject", "submit", "subway",
            "success", "such", "sudden", "suffer", "sugar", "suggest", "suit", "sun", "sunny", "sunset",
            "super", "supply", "support", "suppose", "sure", "surface", "surge", "surprise", "surround", "survey",
            "suspect", "sustain", "swallow", "swamp", "swap", "swarm", "swear", "sweet", "swift", "swim",
            "swing", "switch", "sword", "symbol", "symptom", "syrup", "system", "table", "tackle", "tag",
            "tail", "talent", "talk", "tank", "tape", "target", "task", "taste", "tattoo", "taxi",
            "teach", "team", "tell", "ten", "tenant", "tennis", "tent", "term", "test", "text",
            "thank", "that", "theme", "then", "theory", "there", "they", "thing", "this", "thought",
            "three", "thrive", "throw", "thumb", "thunder", "ticket", "tide", "tiger", "tilt", "timber",
            "time", "tiny", "tip", "tired", "tissue", "title", "toast", "tobacco", "today", "toddler",
            "toe", "together", "toilet", "token", "tomato", "tomorrow", "tone", "tongue", "tonight", "tool",
            "tooth", "top", "topic", "topple", "torch", "tornado", "tortoise", "toss", "total", "tourist",
            "toward", "tower", "town", "toy", "track", "trade", "traffic", "tragic", "train", "transfer",
            "trap", "trash", "travel", "tray", "treat", "tree", "trend", "trial", "tribe", "trick",
            "trigger", "trim", "trip", "trophy", "trouble", "truck", "true", "truly", "trumpet", "trust",
            "truth", "try", "tube", "tuition", "tumble", "tuna", "tunnel", "turkey", "turn", "turtle",
            "twelve", "twenty", "twice", "twin", "twist", "two", "type", "typical", "ugly", "umbrella",
            "unable", "unaware", "uncle", "uncover", "under", "undo", "unfair", "unfold", "unhappy", "uniform",
            "unique", "unit", "universe", "unknown", "unlock", "until", "unusual", "unveil", "update", "upgrade",
            "uphold", "upon", "upper", "upset", "urban", "urge", "usage", "use", "used", "useful",
            "useless", "usual", "utility", "vacant", "vacuum", "vague", "valid", "valley", "valve", "van",
            "vanish", "vapor", "various", "vast", "vault", "vehicle", "velvet", "vendor", "venture", "venue",
            "verb", "verify", "version", "very", "vessel", "veteran", "viable", "vibrant", "vicious", "victory",
            "video", "view", "village", "vintage", "violin", "virtual", "virus", "visa", "visit", "visual",
            "vital", "vivid", "vocal", "voice", "void", "volcano", "volume", "vote", "voyage", "wage",
            "wagon", "wait", "walk", "wall", "walnut", "want", "warfare", "warm", "warrior", "wash",
            "wasp", "waste", "water", "wave", "way", "wealth", "weapon", "wear", "weasel", "weather",
            "web", "wedding", "week", "weird", "welcome", "west", "wet", "whale", "what", "wheat",
            "wheel", "when", "where", "whip", "whisper", "wide", "width", "wife", "wild", "will",
            "win", "window", "wine", "wing", "wink", "winner", "winter", "wire", "wisdom", "wise",
            "wish", "witness", "wolf", "woman", "wonder", "wood", "wool", "word", "work", "world",
            "worry", "worth", "wrap", "wreck", "wrestle", "wrist", "write", "wrong", "yard", "year",
            "yellow", "you", "young", "youth", "zebra", "zero", "zone", "zoo"
        )
        words.toSet()
    }

    fun scanForWallets(): Flow<ScanProgress> = flow {
        val found = mutableListOf<WalletModel>()
        val errors = mutableListOf<String>()
        var totalFiles = 0
        var dirsScanned = 0

        // 1. Skanuj skonfigurowane ścieżki
        for (path in config.scanPaths) {
            val dir = File(path)
            if (!dir.exists() || !dir.canRead()) {
                continue
            }

            val result = scanDirectory(dir)
            found.addAll(result.first)
            totalFiles += result.second
            dirsScanned++
            errors.addAll(result.third)

            emit(ScanProgress(found.toList(), totalFiles, dirsScanned, dir.path, errors.toList(), false))
        }

        // 2. Skanuj MediaStore
        if (config.scanMediaStore) {
            try {
                val mediaResult = scanMediaStore()
                found.addAll(mediaResult.first)
                totalFiles += mediaResult.second
                errors.addAll(mediaResult.third)
                emit(ScanProgress(found.toList(), totalFiles, dirsScanned, "MediaStore", errors.toList(), false))
            } catch (e: Exception) {
                errors.add("MediaStore error: ${e.message}")
            }
        }

        // 3. Skanuj katalogi aplikacji (jeśli root dostępny)
        if (config.scanExternalApps && config.useRootAccess) {
            try {
                val appResult = scanAppDirectories()
                found.addAll(appResult.first)
                totalFiles += appResult.second
                errors.addAll(appResult.third)
            } catch (e: Exception) {
                errors.add("App scan error: ${e.message}")
            }
        }

        // 4. Skanuj obrazy (OCR dla seed phrase na zdjęciach)
        if (config.fileExtensions.any { it == "jpg" || it == "png" || it == "jpeg" }) {
            try {
                val imageResult = scanImages()
                found.addAll(imageResult.first)
                totalFiles += imageResult.second
            } catch (e: Exception) {
                errors.add("Image scan error: ${e.message}")
            }
        }

        emit(ScanProgress(found.toList(), totalFiles, dirsScanned, "", errors.toList(), true))
    }

    private suspend fun scanDirectory(dir: File): Triple<List<WalletModel>, Int, List<String>> {
        val found = mutableListOf<WalletModel>()
        var files = 0
        val errors = mutableListOf<String>()

        try {
            dir.walkTopDown()
                .maxDepth(config.maxDepth)
                .filter { file ->
                    if (file.isHidden && !config.scanHiddenDirectories) return@filter false
                    if (file.isDirectory) return@filter false
                    if (file.length() > config.maxFileSizeBytes) return@filter false
                    if (file.length() == 0L) return@filter false
                    files++
                    true
                }
                .forEach { file ->
                    val wallet = analyzeFile(file)
                    if (wallet != null) found.add(wallet)
                }
        } catch (e: Exception) {
            errors.add("Error scanning ${dir.path}: ${e.message}")
        }

        return Triple(found, files, errors)
    }

    private suspend fun scanMediaStore(): Triple<List<WalletModel>, Int, List<String>> {
        val found = mutableListOf<WalletModel>()
        var files = 0
        val errors = mutableListOf<String>()

        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.TITLE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE
            )

            // Szukaj plików które mogą być portfelami
            val selection = buildString {
                append("(")
                append("${MediaStore.Files.FileColumns.DATA} LIKE ?")
                append(" OR ${MediaStore.Files.FileColumns.DATA} LIKE ?")
                append(" OR ${MediaStore.Files.FileColumns.DATA} LIKE ?")
                append(" OR ${MediaStore.Files.FileColumns.DATA} LIKE ?")
                append(" OR ${MediaStore.Files.FileColumns.DATA} LIKE ?")
                append(")")
            }

            val selectionArgs = arrayOf(
                "%wallet%",
                "%backup%",
                "%seed%",
                "%keystore%",
                "%UTC--%"
            )

            val cursor: Cursor? = context.contentResolver.query(
                uri, projection, selection, selectionArgs, null
            )

            cursor?.use { c ->
                val dataIndex = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val sizeIndex = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)

                while (c.moveToNext()) {
                    val dataPath = c.getString(dataIndex) ?: continue
                    val fileSize = c.getLong(sizeIndex)

                    if (fileSize > config.maxFileSizeBytes || fileSize == 0L) continue

                    val file = File(dataPath)
                    if (file.exists() && file.canRead()) {
                        files++
                        val wallet = analyzeFile(file)
                        if (wallet != null) found.add(wallet)
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("MediaStore scan error: ${e.message}")
        }

        return Triple(found, files, errors)
    }

    private suspend fun scanAppDirectories(): Triple<List<WalletModel>, Int, List<String>> {
        val found = mutableListOf<WalletModel>()
        var files = 0
        val errors = mutableListOf<String>()

        val appDirs = listOf(
            "/data/data/io.metamask",
            "/data/data/com.metamask",
            "/data/data/com.trustapp.wallet",
            "/data/data/com.binance.dev",
            "/data/data/com.binance",
            "/data/data/com.coinbase.android",
            "/data/data/com.exodus",
            "/data/data/com.mycelium.wallet",
            "/data/data/com.ledger.live",
            "/data/data/com.blockchain",
            "/data/data/com.bitcoin.wallet",
            "/data/data/org.bitcoin.wallet",
            "/data/data/com.samourai.wallet",
            "/data/data/com.electrum.app",
            "/data/data/com.atomicwallet",
            "/data/data/com.defi.wallet",
            "/data/data/com.uniswap",
            "/data/data/com.1inch",
            "/data/data/com.rainbow.me",
            "/data/data/com.phantom.app",
            "/data/data/com.solana",
            "/data/data/com.tron.wallet",
            "/data/data/com.theta",
            "/data/data/com.terra",
            "/data/data/com.crypto",
            "/data/data/com.bitpay",
            "/data/data/com.pluswallet"
        )

        for (dirPath in appDirs) {
            val dir = File(dirPath)
            if (dir.exists() && dir.canRead()) {
                try {
                    val subDir = dir.walkTopDown().maxDepth(5)
                    subDir.forEach { file ->
                        if (!file.isDirectory && file.length() > 0 && file.length() <= 500000) {
                            files++
                            val wallet = analyzeFile(file)
                            if (wallet != null) found.add(wallet)
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Cannot read $dirPath: ${e.message}")
                }
            }
        }

        return Triple(found, files, errors)
    }

    private suspend fun scanImages(): Triple<List<WalletModel>, Int, List<String>> {
        val found = mutableListOf<WalletModel>()
        var files = 0
        val errors = mutableListOf<String>()

        // Wyszukaj obrazy które mogą zawierać seed phrase
        // W pełnej wersji: OCR (Tesseract) na obrazach
        // Uproszczenie: szukamy plików z seed w nazwie
        try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.TITLE,
                MediaStore.Images.Media.SIZE
            )
            val selection = "${MediaStore.Images.Media.TITLE} LIKE ? OR ${MediaStore.Images.Media.TITLE} LIKE ?"
            val selectionArgs = arrayOf("%seed%", "%phrase%", "%wallet%", "%backup%", "%recovery%", "%mnemonic%")

            val cursor = context.contentResolver.query(
                uri, projection, selection, selectionArgs, null
            )

            cursor?.use { c ->
                val dataIndex = c.getColumnIndex(MediaStore.Images.Media.DATA)
                while (c.moveToNext()) {
                    val dataPath = c.getString(dataIndex) ?: continue
                    val file = File(dataPath)
                    if (file.exists() && file.length() <= config.maxFileSizeBytes) {
                        files++
                        // W pełnej wersji: OCR + sprawdzanie czy zawiera 12/24 słów
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Image scan error: ${e.message}")
        }

        return Triple(found, files, errors)
    }

    private fun analyzeFile(file: File): WalletModel? {
        val content: String = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                file.readBytes().let { bytes ->
                    // Próba odczytu jako UTF-8
                    bytes.decodeToString()
                }
            } catch (e2: Exception) {
                return null
            }
        }

        if (content.isBlank() || content.length < 10) return null

        // 1. wallet.dat
        if (file.name.equals("wallet.dat", ignoreCase = true)) {
            val isEnc = content.contains("encrypted_master") || content.contains("encrypted")
            return WalletModel(
                fileName = file.name,
                fullPath = file.absolutePath,
                fileSizeBytes = file.length(),
                walletType = WalletType.BITCOIN_CORE,
                cryptoType = CryptoType.BTC,
                isEncrypted = isEnc,
                encryptionType = if (isEnc) EncryptionType.BITCOIN_WALLET_DAT else EncryptionType.NONE,
                status = WalletStatus.PENDING
            )
        }

        // 2. Ethereum keystore (UTC--)
        if (file.name.startsWith("UTC--") && file.extension == "json") {
            try {
                val json = JsonParser.parseString(content).asJsonObject
                if (json.has("address") && (json.has("crypto") || json.has("Crypto"))) {
                    var addr = json.get("address").asString
                    if (!addr.startsWith("0x")) addr = "0x$addr"
                    return WalletModel(
                        fileName = file.name,
                        fullPath = file.absolutePath,
                        fileSizeBytes = file.length(),
                        walletType = WalletType.ETHEREUM_KEYSTORE,
                        cryptoType = CryptoType.ETH,
                        keystoreJson = content,
                        address = addr,
                        isEncrypted = true,
                        encryptionType = EncryptionType.ETHEREUM_KEYSTORE,
                        status = WalletStatus.PROTECTED
                    )
                }
            } catch (e: Exception) { /* not a valid keystore */ }
        }

        // 3. Mnemonik (12/24 słowa)
        // Sprawdź czy wszystkie słowa są z listy BIP39
        val words = content.lowercase().trim().split(Regex("\\s+"))
        if (words.size == 12 || words.size == 18 || words.size == 24) {
            val allBip39 = words.all { it in bip39WordList }
            if (allBip39) {
                return WalletModel(
                    fileName = file.name,
                    fullPath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    walletType = WalletType.MNEMONIC,
                    mnemonic = content.trim(),
                    status = WalletStatus.PENDING
                )
            }
        }

        // 4. ETH private key (0x...64)
        val ethKeyRegex = Regex("""0x[a-fA-F0-9]{64}""")
        val ethKeyMatch = ethKeyRegex.find(content)
        if (ethKeyMatch != null) {
            return WalletModel(
                fileName = file.name,
                fullPath = file.absolutePath,
                fileSizeBytes = file.length(),
                walletType = WalletType.PRIVATE_KEY,
                cryptoType = CryptoType.ETH,
                privateKey = ethKeyMatch.value,
                status = WalletStatus.PENDING
            )
        }

        // 5. WIF (BTC private key)
        val wifRegex = Regex("""[5KL][1-9A-HJ-NP-Za-km-z]{51}""")
        val wifMatch = wifRegex.find(content)
        if (wifMatch != null) {
            return WalletModel(
                fileName = file.name,
                fullPath = file.absolutePath,
                fileSizeBytes = file.length(),
                walletType = WalletType.PRIVATE_KEY,
                cryptoType = CryptoType.BTC,
                privateKey = wifMatch.value,
                status = WalletStatus.PENDING
            )
        }

        // 6. JSON wallet exports
        if (file.extension == "json" || file.extension == "txt") {
            try {
                val json = JsonParser.parseString(content).asJsonObject

                // Has privateKey field
                for (key in listOf("privateKey", "private_key", "secret", "key", "privKey")) {
                    if (json.has(key)) {
                        val pk = json.get(key)?.asString
                        if (pk != null && pk.length in 50..70) {
                            return WalletModel(
                                fileName = file.name,
                                fullPath = file.absolutePath,
                                fileSizeBytes = file.length(),
                                walletType = WalletType.PRIVATE_KEY,
                                privateKey = pk,
                                status = WalletStatus.PENDING
                            )
                        }
                    }
                }

                // Has data+walletType (MetaMask/Trust export)
                if (json.has("data") && json.has("walletType")) {
                    return WalletModel(
                        fileName = file.name,
                        fullPath = file.absolutePath,
                        fileSizeBytes = file.length(),
                        walletType = WalletType.BROWSER_EXTENSION,
                        status = WalletStatus.PENDING
                    )
                }

                // Encrypted backup
                if (json.has("encrypted") || json.has("ciphertext") || json.has("iv")) {
                    return WalletModel(
                        fileName = file.name,
                        fullPath = file.absolutePath,
                        fileSizeBytes = file.length(),
                        walletType = WalletType.MOBILE_BACKUP,
                        isEncrypted = true,
                        encryptionType = EncryptionType.AES_256,
                        keystoreJson = content,
                        status = WalletStatus.PROTECTED
                    )
                }
            } catch (e: Exception) { /* not JSON */ }
        }

        // 7. CSV exchange export
        if (file.extension == "csv") {
            val lowerContent = content.lowercase()
            if (lowerContent.contains("address") &&
                (lowerContent.contains("amount") || lowerContent.contains("balance") || lowerContent.contains("value"))
            ) {
                // Wyciągnij adresy z CSV
                val lines = content.lines().drop(1)
                val addresses = mutableListOf<String>()
                for (line in lines) {
                    val parts = line.split(",")
                    for (part in parts) {
                        val trimmed = part.trim()
                        if (trimmed.startsWith("0x") && trimmed.length == 42) {
                            addresses.add(trimmed)
                        }
                    }
                }
                if (addresses.isNotEmpty()) {
                    return WalletModel(
                        fileName = file.name,
                        fullPath = file.absolute
if (addresses.isNotEmpty()) {
                        return WalletModel(
                            fileName = file.name,
                            fullPath = file.absolutePath,
                            fileSizeBytes = file.length(),
                            walletType = WalletType.EXCHANGE_EXPORT,
                            address = addresses.first(),
                            status = WalletStatus.PENDING,
                            tags = listOf("csv", "exchange")
                        )
                    }
                }
            }

            // 8. Generic text file z kluczem
            if (file.extension in listOf("txt", "bak", "backup", "old", "priv", "key", "pem")) {
                // Szukaj 64-znakowego hex (klucz prywatny)
                val hexKeyRegex = Regex("""[a-fA-F0-9]{64}""")
                val hexMatch = hexKeyRegex.find(content)
                if (hexMatch != null) {
                    return WalletModel(
                        fileName = file.name,
                        fullPath = file.absolutePath,
                        fileSizeBytes = file.length(),
                        walletType = WalletType.PRIVATE_KEY,
                        privateKey = hexMatch.value,
                        status = WalletStatus.PENDING
                    )
                }
            }

            // 9. Plik z "address" w nazwie
            if (file.name.contains("address", ignoreCase = true) && file.extension == "txt") {
                val addrRegex = Regex("""0x[a-fA-F0-9]{40}""")
                val addrMatch = addrRegex.find(content)
                if (addrMatch != null) {
                    return WalletModel(
                        fileName = file.name,
                        fullPath = file.absolutePath,
                        fileSizeBytes = file.length(),
                        walletType = WalletType.CUSTOM,
                        address = addrMatch.value,
                        status = WalletStatus.PENDING
                    )
                }
            }

            return null
        }
    }
}