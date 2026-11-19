package com.emirzinciri.robotkomutan.logic.command

/** Komut kartı kategorileri; arayüzde renk ve gruplama için kullanılır. */
enum class CommandCategory(val title: String) {
    MOVE("Hareket"), ACTION("Eylem"), ENERGY("Enerji"), SIGNAL("Sinyal"), FLOW("Akış")
}

/**
 * Komut kataloğu. Yalnızca simülasyon motorunda gerçek karşılığı olan komutlar burada bulunur;
 * yeni komutlar motorla birlikte eklenir (bkz. docs/ROADMAP.md).
 *
 * @param energyCost Komut çalıştığında harcanan enerji (enerji kapalı bölümlerde yok sayılır).
 * @param ticks Komutun harcadığı mantıksal adım sayısı (akış komutları 0 adım sürer).
 * @param hasArg Kartın ayarlanabilir bir sayısı var mı (ör. Tekrar ×3).
 */
enum class CommandType(
    val title: String,
    val shortLabel: String,
    val category: CommandCategory,
    val energyCost: Int,
    val ticks: Int,
    val description: String,
    val hasArg: Boolean = false,
    val argRange: IntRange = 0..0,
    val defaultArg: Int = 0,
    val opensBlock: Boolean = false,
) {
    FORWARD("İleri git", "İleri", CommandCategory.MOVE, 1, 1,
        "Robot baktığı yöne bir hücre ilerler. Duvar, kapalı kapı veya başka bir robot varsa çarpar ve yerinde kalır."),
    BACK("Geri git", "Geri", CommandCategory.MOVE, 1, 1,
        "Robot yönünü değiştirmeden bir hücre geri gider."),
    TURN_LEFT("Sola dön", "Sola", CommandCategory.MOVE, 0, 1,
        "Robot yerinde 90° sola döner."),
    TURN_RIGHT("Sağa dön", "Sağa", CommandCategory.MOVE, 0, 1,
        "Robot yerinde 90° sağa döner."),
    WAIT("Bekle", "Bekle", CommandCategory.MOVE, 0, 1,
        "Robot bir adım boyunca bekler. Zamanlama için kullanılır."),
    JUMP("Zıpla", "Zıpla", CommandCategory.MOVE, 2, 1,
        "Robot iki hücre ileri zıplar; aradaki çukur, boşluk veya kutunun üstünden geçer. Duvar ve kapalı kapının üstünden geçemez. Kutu taşırken zıplanamaz."),
    PICK("Nesne al", "Al", CommandCategory.ACTION, 1, 1,
        "Robotun önündeki kutuyu kaldırır. Robot aynı anda tek kutu taşır."),
    DROP("Nesne bırak", "Bırak", CommandCategory.ACTION, 0, 1,
        "Taşınan kutuyu robotun önündeki boş hücreye bırakır."),
    PUSH("İt", "İt", CommandCategory.ACTION, 2, 1,
        "Önündeki kutuyu bir hücre iterek kendisi de ilerler. Kutunun gideceği hücre boş olmalıdır. Çukura itilen kutu çukuru doldurur."),
    PULL("Çek", "Çek", CommandCategory.ACTION, 2, 1,
        "Robot bir hücre geri giderken önündeki kutuyu da kendine doğru çeker."),
    PRESS("Butona bas", "Bas", CommandCategory.ACTION, 1, 1,
        "Robotun önündeki kumanda paneline basar: anahtar kapıları açar/kapatır, zamanlı düğme kapıları birkaç adım açık tutar."),
    COLLECT("Enerji topla", "Topla", CommandCategory.ENERGY, 0, 1,
        "Üzerinde durduğu enerji hücresini toplar veya şarj istasyonunda enerjisini doldurur."),
    TRANSFER("Enerji aktar", "Aktar", CommandCategory.ENERGY, 0, 1,
        "Önündeki enerji soketine ya da robota 3 birime kadar enerji aktarır. Dolan soket bağlı kapıları kalıcı olarak açar."),
    SEND("Mesaj gönder", "Gönder", CommandCategory.SIGNAL, 0, 1,
        "Diğer bütün robotlara bir sinyal gönderir. Sinyal bir sonraki adımda alınır."),
    WAIT_MSG("Mesaj bekle", "Mesaj?", CommandCategory.SIGNAL, 0, 1,
        "Bir sinyal gelene kadar bekler; sinyal gelince onu tüketip devam eder."),
    REPEAT("Tekrar et", "Tekrar", CommandCategory.FLOW, 0, 0,
        "Kendisiyle 'Blok sonu' arasındaki komutları seçilen sayı kadar tekrarlar.",
        hasArg = true, argRange = 2..9, defaultArg = 2, opensBlock = true),
    IF_CLEAR("Önü boşsa", "Boşsa", CommandCategory.FLOW, 0, 0,
        "Robotun önündeki hücreye girilebiliyorsa bloğu çalıştırır, değilse 'Blok sonu'na atlar.", opensBlock = true),
    IF_BLOCKED("Önü kapalıysa", "Kapalıysa", CommandCategory.FLOW, 0, 0,
        "Robotun önü kapalıysa bloğu çalıştırır, değilse 'Blok sonu'na atlar.", opensBlock = true),
    END("Blok sonu", "Son", CommandCategory.FLOW, 0, 0,
        "Tekrar ve koşul bloklarını kapatır."),
    STOP("Acil durdur", "Dur", CommandCategory.FLOW, 0, 0,
        "Robotun programını hemen sonlandırır.");

    val isFlow: Boolean get() = category == CommandCategory.FLOW
}

/** Komut zincirindeki tek kart. */
data class Instr(val type: CommandType, val arg: Int = type.defaultArg) {
    init {
        if (type.hasArg) require(arg in type.argRange) { "${type.title} için geçersiz değer: $arg" }
    }

    /** Kayıt ve bölüm dosyalarında kullanılan metin biçimi: "FORWARD", "REPEAT:3". */
    fun encode(): String = if (type.hasArg) "${type.name}:$arg" else type.name

    companion object {
        fun decode(token: String): Instr {
            val name = token.substringBefore(':').trim()
            val type = CommandType.valueOf(name)
            val arg = if (':' in token) token.substringAfter(':').trim().toInt() else type.defaultArg
            return Instr(type, arg)
        }
    }
}

/** Derleme hatası: arayüzde hatalı kartın vurgulanması için konum bilgisi taşır. */
data class ProgramError(val index: Int, val message: String)

/**
 * Düz kart listesinden derlenmiş program. Bloklar (Tekrar/Koşul ... Blok sonu) düz listede
 * işaretlerle tutulur; böylece mobil ekranda tek satırlı sürükle-bırak düzenleme mümkün olur.
 */
class Program private constructor(
    val instrs: List<Instr>,
    /** Blok açan kartın indeksi -> eşleşen Blok sonu indeksi. */
    val blockEnd: IntArray,
    /** Blok sonu indeksi -> açan kartın indeksi. */
    val blockStart: IntArray,
) {
    val size get() = instrs.size

    /** Yıldız ve sınır hesabında sayılan kart sayısı (Blok sonu kartları sayılmaz). */
    val cardCount: Int get() = instrs.count { it.type != CommandType.END }

    companion object {
        val EMPTY: Program = compile(emptyList()).getOrThrow()

        fun compile(instrs: List<Instr>): Result<Program> {
            val end = IntArray(instrs.size) { -1 }
            val start = IntArray(instrs.size) { -1 }
            val stack = ArrayDeque<Int>()
            instrs.forEachIndexed { i, ins ->
                when {
                    ins.type.opensBlock -> stack.addLast(i)
                    ins.type == CommandType.END -> {
                        val open = stack.removeLastOrNull()
                            ?: return Result.failure(ProgramException(ProgramError(i, "Bu 'Blok sonu' kartına ait bir blok başlangıcı yok.")))
                        end[open] = i
                        start[i] = open
                    }
                }
            }
            stack.lastOrNull()?.let { open ->
                return Result.failure(ProgramException(ProgramError(open, "'${instrs[open].type.title}' bloğu 'Blok sonu' ile kapatılmalı.")))
            }
            return Result.success(Program(instrs.toList(), end, start))
        }

        fun parse(tokens: List<String>): Result<Program> =
            runCatching { tokens.map(Instr::decode) }.fold({ compile(it) }, { Result.failure(it) })
    }
}

class ProgramException(val error: ProgramError) : IllegalArgumentException(error.message)
