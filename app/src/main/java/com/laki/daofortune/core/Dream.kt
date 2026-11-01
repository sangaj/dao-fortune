package com.laki.daofortune.core

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The six spirits (liu shen) that sit on the plate.
 *
 * Only the four beasts of the 分野 are used as the general mapping — the
 * Azure Dragon holds 寅卯辰, the Vermilion Bird 巳午未, the White Tiger 申酉戌
 * and the Dark Warrior 亥子丑.  The Flying Snake appears when a snake itself
 * turns up in the dream, which is the one place it is traditionally read.
 */
enum class DreamSpirit(val en: String, val chinese: String, val element: WuXing) {
    QING_LONG("Azure Dragon", "青龙", WuXing.WOOD),
    ZHU_QUE("Vermilion Bird", "朱雀", WuXing.FIRE),
    BAI_HU("White Tiger", "白虎", WuXing.METAL),
    XUAN_WU("Dark Warrior", "玄武", WuXing.WATER),
    TENG_SHE("Flying Snake", "螣蛇", WuXing.FIRE)
}

/** What kind of image the dream showed. */
enum class DreamKind(val en: String) {
    CREATURE("creature"),
    DIRECTION("direction"),
    NUMBER("number"),
    HOUR("hour"),
    COLOUR("colour"),
    NATURE("element"),
    WEALTH("sign of money")
}

/**
 * One image taken out of the dream text.
 *
 * A token carries whatever the rules need: a creature carries its branch and
 * its spirit, a direction carries the mountain it points at, a colour carries
 * the element it stands for.
 */
data class DreamToken(
    val kind: DreamKind,
    /** The word as the reader wrote it, for display. */
    val term: String,
    /** The original term, where there is one. */
    val chinese: String,
    val branch: Branch? = null,
    val element: WuXing? = null,
    /** The mountain a direction points at. */
    val palace: Palace? = null,
    val spirit: DreamSpirit? = null,
    /** True for the traditional sign of money. */
    val wealth: Boolean = false
)

/**
 * Everything the app could read out of a dream.
 *
 * Nothing here is kept: the text lives in memory while the sheet is open and
 * is gone when it closes.
 */
data class DreamImage(
    val tokens: List<DreamToken>,
    /** The omen the reading is built on. */
    val principal: DreamToken?,
    /** The double-hour the dreamer woke in, when it is known. */
    val hourBranch: Branch?,
    /** The element the dream is mostly made of. */
    val element: WuXing?,
    /** A stable seed, so the same dream always draws the same plate. */
    val seed: Int
)

/** A finished reading of a dream. */
data class DreamReading(
    val score: Int,
    val verdict: Verdict,
    val headline: String,
    val summary: String,
    val insights: List<String>,
    val advice: List<String>,
    val factors: List<Factor>,
    val image: DreamImage,
    /** Which night the dream belongs to. */
    val contextLabel: String
)

/**
 * The vocabulary of the dream.
 *
 * The lexicon is deliberately bounded: directions, the twelve animals, the
 * elements, colours, numbers and hours. Those are the images a classical
 * reading can actually work with — everything else in a dream is atmosphere,
 * and pretending otherwise would be the fortune-telling the app refuses to do.
 *
 * English words are matched on word boundaries (so "rat" never fires inside
 * "grateful"); the Chinese terms are matched as plain substrings.
 */
internal object DreamLexicon {

    /** One entry of the vocabulary. */
    internal data class Lexeme(
        val words: List<String>,
        val kind: DreamKind,
        val term: String,
        val chinese: String,
        val branch: Branch? = null,
        val element: WuXing? = null,
        val palace: Palace? = null,
        val spirit: DreamSpirit? = null,
        val wealth: Boolean = false
    )

    /** The twelve animals, with the branch and the spirit they carry. */
    private fun animal(name: String, chinese: String, branch: Branch, spirit: DreamSpirit? = null) = Lexeme(
        words = listOf(name),
        kind = DreamKind.CREATURE,
        term = name.replaceFirstChar { it.uppercase() },
        chinese = chinese,
        branch = branch,
        element = branch.element,
        palace = Branch.palaceOf(branch),
        spirit = spirit ?: spiritOf(branch)
    )

    /** The beast that holds a branch, by 分野. */
    fun spiritOf(branch: Branch): DreamSpirit = when (branch) {
        Branch.YIN, Branch.MAO, Branch.CHEN -> DreamSpirit.QING_LONG
        Branch.SI, Branch.WU, Branch.WEI -> DreamSpirit.ZHU_QUE
        Branch.SHEN, Branch.YOU, Branch.XU -> DreamSpirit.BAI_HU
        Branch.HAI, Branch.ZI, Branch.CHOU -> DreamSpirit.XUAN_WU
    }

    private fun element(words: List<String>, element: WuXing, chinese: String) = Lexeme(
        words = words,
        kind = DreamKind.NATURE,
        term = element.en,
        chinese = chinese,
        element = element,
        palace = palaceOfElement(element)
    )

    /** The mountain a phase points at — the same bearing the wealth uses. */
    fun palaceOfElement(element: WuXing): Palace = when (element) {
        WuXing.WOOD -> Palace.MAO
        WuXing.FIRE -> Palace.WU
        WuXing.METAL -> Palace.YOU
        WuXing.WATER -> Palace.ZI
        WuXing.EARTH -> Palace.CHEN
    }

    private fun colour(words: List<String>, element: WuXing, chinese: String) = Lexeme(
        words = words,
        kind = DreamKind.COLOUR,
        term = words.first().replaceFirstChar { it.uppercase() },
        chinese = chinese,
        element = element,
        palace = palaceOfElement(element)
    )

    private fun direction(words: List<String>, palace: Palace, term: String) = Lexeme(
        words = words,
        kind = DreamKind.DIRECTION,
        term = term,
        chinese = palace.pinyin,
        branch = Palace.branchOf(palace),
        element = palace.element,
        palace = palace
    )

    private fun number(words: List<String>, branch: Branch) = Lexeme(
        words = words,
        kind = DreamKind.NUMBER,
        term = words.first(),
        chinese = branch.pinyin,
        branch = branch,
        element = branch.element,
        palace = Branch.palaceOf(branch)
    )

    /** The whole vocabulary, in the order it is searched. */
    val entries: List<Lexeme> = buildList {
        // --- the twelve animals -------------------------------------------
        add(animal("rat", "鼠", Branch.ZI))
        add(animal("mouse", "鼠", Branch.ZI))
        add(animal("ox", "牛", Branch.CHOU))
        add(animal("bull", "牛", Branch.CHOU))
        add(animal("cow", "牛", Branch.CHOU))
        add(animal("tiger", "虎", Branch.YIN))
        add(animal("rabbit", "兔", Branch.MAO))
        add(animal("hare", "兔", Branch.MAO))
        add(animal("dragon", "龙", Branch.CHEN))
        add(animal("snake", "蛇", Branch.SI, DreamSpirit.TENG_SHE))
        add(animal("serpent", "蛇", Branch.SI, DreamSpirit.TENG_SHE))
        add(animal("horse", "马", Branch.WU))
        add(animal("goat", "羊", Branch.WEI))
        add(animal("sheep", "羊", Branch.WEI))
        add(animal("monkey", "猴", Branch.SHEN))
        add(animal("ape", "猿", Branch.SHEN))
        add(animal("rooster", "鸡", Branch.YOU))
        add(animal("cock", "鸡", Branch.YOU))
        add(animal("chicken", "鸡", Branch.YOU))
        add(animal("hen", "鸡", Branch.YOU))
        add(animal("dog", "狗", Branch.XU))
        add(animal("pig", "猪", Branch.HAI))
        add(animal("boar", "猪", Branch.HAI))

        // --- direction ------------------------------------------------------
        // The compound directions come first: "north-east" has to be claimed as
        // a whole, or the plain "north" takes the first half of it.
        add(direction(listOf("north-east", "northeast", "north east"), Palace.GEN, "North-East"))
        add(direction(listOf("south-east", "southeast", "south east"), Palace.XUN, "South-East"))
        add(direction(listOf("south-west", "southwest", "south west"), Palace.KUN, "South-West"))
        add(direction(listOf("north-west", "northwest", "north west"), Palace.QIAN, "North-West"))
        add(direction(listOf("north"), Palace.ZI, "Due North"))
        add(direction(listOf("northern"), Palace.ZI, "Due North"))
        add(direction(listOf("south"), Palace.WU, "Due South"))
        add(direction(listOf("southern"), Palace.WU, "Due South"))
        add(direction(listOf("east"), Palace.MAO, "Due East"))
        add(direction(listOf("eastern"), Palace.MAO, "Due East"))
        add(direction(listOf("west"), Palace.YOU, "Due West"))
        add(direction(listOf("western"), Palace.YOU, "Due West"))
        add(direction(listOf("up", "upward", "above", "sky", "ceiling", "heaven", "high"), Palace.QIAN, "Upward"))
        add(direction(listOf("down", "downward", "below", "underground", "beneath", "buried"), Palace.KUN, "Downward"))

        // --- the five phases, as things in a dream ---------------------------
        add(
            element(
                listOf(
                    "water", "river", "sea", "ocean", "lake", "rain", "flood", "wave", "waves",
                    "ice", "snow", "drowning", "swimming", "pond", "puddle", "rainwater", "sea"
                ),
                WuXing.WATER,
                "水"
            )
        )
        add(
            element(
                listOf(
                    "fire", "flame", "flames", "blaze", "burning", "burnt", "torch", "candle", "lamp",
                    "bonfire", "volcano", "furnace", "inferno", "lava"
                ),
                WuXing.FIRE,
                "火"
            )
        )
        add(
            element(
                listOf(
                    "tree", "trees", "forest", "wood", "wooden", "bamboo", "leaf", "leaves", "branch",
                    "bush", "grass", "plant", "flower", "flowers", "garden", "park", "vine", "plank"
                ),
                WuXing.WOOD,
                "木"
            )
        )
        add(
            element(
                listOf(
                    "metal", "iron", "steel", "knife", "sword", "bell", "coin",
                    "coins", "key", "car", "train", "bus", "machine", "engine", "phone", "computer",
                    "mirror", "needle", "nails", "screw", "steel bars"
                ),
                WuXing.METAL,
                "金"
            )
        )
        add(
            element(
                listOf(
                    "earth", "soil", "mud", "wall", "walls", "mountain", "hill", "stone", "rock",
                    "sand", "ground", "house", "home", "room", "door", "city", "street", "road",
                    "field", "farm", "food", "rice", "bread", "meat", "egg", "eggs", "cake", "fruit",
                    "vegetable", "table", "bed", "chair", "box", "brick", "clay", "pottery", "cemetery",
                    "grave", "graveyard", "coffin"
                ),
                WuXing.EARTH,
                "土"
            )
        )

        // --- colours, by the classical five ---------------------------------
        add(colour(listOf("green", "emerald", "olive"), WuXing.WOOD, "青"))
        add(colour(listOf("red", "crimson", "scarlet"), WuXing.FIRE, "赤"))
        add(colour(listOf("yellow", "brown", "ochre"), WuXing.EARTH, "黄"))
        add(colour(listOf("white", "silver", "grey", "gray"), WuXing.METAL, "白"))
        add(colour(listOf("black", "dark"), WuXing.WATER, "黑"))

        // --- the sign of money ----------------------------------------------
        //
        // It comes after the colours and the phases but before the words that
        // would otherwise claim "gold" and "coins" as plain metal.
        add(
            Lexeme(
                words = listOf("money", "coin", "coins", "cash", "gold", "gold coins", "fortune", "wealth", "salary"),
                kind = DreamKind.WEALTH,
                term = "Money",
                chinese = "财",
                element = WuXing.METAL,
                palace = Palace.YOU,
                wealth = true
            )
        )

        // --- numbers, which the tradition reads as branches -------------------
        add(number(listOf("one", "1"), Branch.ZI))
        add(number(listOf("two", "2"), Branch.CHOU))
        add(number(listOf("three", "3"), Branch.YIN))
        add(number(listOf("four", "4"), Branch.MAO))
        add(number(listOf("five", "5"), Branch.CHEN))
        add(number(listOf("six", "6"), Branch.SI))
        add(number(listOf("seven", "7"), Branch.WU))
        add(number(listOf("eight", "8"), Branch.WEI))
        add(number(listOf("nine", "9"), Branch.SHEN))
        add(number(listOf("ten", "10"), Branch.YOU))
        add(number(listOf("eleven", "11"), Branch.XU))
        add(number(listOf("twelve", "12"), Branch.HAI))
    }

    /** A compiled matcher for one lexeme. */
    internal class Matcher(val lexeme: Lexeme, val patterns: List<Regex>)

    /** All the matchers, compiled once. */
    val matchers: List<Matcher> = entries.map { lexeme ->
        val patterns = lexeme.words.map { word ->
            if (word.any { it.code > 0x2E80 }) {
                Regex(Regex.escape(word))
            } else {
                Regex("\\b" + Regex.escape(word) + "(s|es)?\\b", RegexOption.IGNORE_CASE)
            }
        }
        Matcher(lexeme, patterns)
    }

    /** Word order for the numbered branches, so "eleven" is not read as "one". */
    private val numberWords = listOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve"
    )

    /** All the number lexemes, longest word first. */
    val numberMatchers: List<Matcher> = matchers
        .filter { it.lexeme.kind == DreamKind.NUMBER }
        .sortedByDescending { matcher -> matcher.lexeme.words.maxOf { it.length } }
        .let { list ->
            // keep the spelled-out words ahead of the digits
            list.sortedBy { matcher -> matcher.lexeme.words.all { w -> w in numberWords } }
        }
}
