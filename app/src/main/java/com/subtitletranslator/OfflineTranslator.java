package com.subtitletranslator;

import android.content.Context;
import java.util.*;

public class OfflineTranslator {

    private Context context;

    private static final Map<String, String> EN_ES = new HashMap<>();
    private static final Map<String, String> PHRASES_EN_ES = new HashMap<>();

    static {
        PHRASES_EN_ES.put("how are you", "¿cómo estás?");
        PHRASES_EN_ES.put("good morning", "buenos días");
        PHRASES_EN_ES.put("good afternoon", "buenas tardes");
        PHRASES_EN_ES.put("good evening", "buenas noches");
        PHRASES_EN_ES.put("good night", "buenas noches");
        PHRASES_EN_ES.put("thank you", "gracias");
        PHRASES_EN_ES.put("thank you very much", "muchas gracias");
        PHRASES_EN_ES.put("you're welcome", "de nada");
        PHRASES_EN_ES.put("i don't know", "no sé");
        PHRASES_EN_ES.put("i don't understand", "no entiendo");
        PHRASES_EN_ES.put("of course", "por supuesto");
        PHRASES_EN_ES.put("no problem", "no hay problema");
        PHRASES_EN_ES.put("see you later", "hasta luego");
        PHRASES_EN_ES.put("for example", "por ejemplo");
        PHRASES_EN_ES.put("as a result", "como resultado");
        PHRASES_EN_ES.put("on the other hand", "por otro lado");
        PHRASES_EN_ES.put("in addition", "además");
        PHRASES_EN_ES.put("first of all", "primero que todo");
        PHRASES_EN_ES.put("in conclusion", "en conclusión");
        PHRASES_EN_ES.put("in summary", "en resumen");
        PHRASES_EN_ES.put("let me explain", "déjame explicar");
        PHRASES_EN_ES.put("according to", "según");
        PHRASES_EN_ES.put("i think that", "creo que");
        PHRASES_EN_ES.put("i believe that", "creo que");
        PHRASES_EN_ES.put("it seems that", "parece que");
        PHRASES_EN_ES.put("the fact is", "el hecho es que");
        PHRASES_EN_ES.put("we need to", "necesitamos");
        PHRASES_EN_ES.put("you need to", "necesitas");
        PHRASES_EN_ES.put("keep in mind", "ten en cuenta");
        PHRASES_EN_ES.put("by the way", "por cierto");
        PHRASES_EN_ES.put("at the same time", "al mismo tiempo");
        PHRASES_EN_ES.put("more or less", "más o menos");
        PHRASES_EN_ES.put("as soon as possible", "lo antes posible");
        PHRASES_EN_ES.put("right now", "ahora mismo");
        PHRASES_EN_ES.put("from now on", "a partir de ahora");
        PHRASES_EN_ES.put("once again", "una vez más");
        PHRASES_EN_ES.put("so far so good", "hasta ahora todo bien");
        PHRASES_EN_ES.put("let's move on", "sigamos adelante");
        PHRASES_EN_ES.put("the point is", "el punto es");
        PHRASES_EN_ES.put("the question is", "la pregunta es");
        PHRASES_EN_ES.put("the answer is", "la respuesta es");
        PHRASES_EN_ES.put("the problem is", "el problema es");
        PHRASES_EN_ES.put("the goal is", "el objetivo es");
        PHRASES_EN_ES.put("the idea is", "la idea es");

        EN_ES.put("the", "el"); EN_ES.put("a", "un"); EN_ES.put("an", "un");
        EN_ES.put("i", "yo"); EN_ES.put("you", "tú"); EN_ES.put("he", "él");
        EN_ES.put("she", "ella"); EN_ES.put("we", "nosotros"); EN_ES.put("they", "ellos");
        EN_ES.put("it", "eso"); EN_ES.put("my", "mi"); EN_ES.put("your", "tu");
        EN_ES.put("his", "su"); EN_ES.put("her", "su"); EN_ES.put("our", "nuestro");
        EN_ES.put("their", "su"); EN_ES.put("this", "este"); EN_ES.put("that", "ese");
        EN_ES.put("these", "estos"); EN_ES.put("those", "esos"); EN_ES.put("here", "aquí");
        EN_ES.put("there", "allí"); EN_ES.put("where", "donde"); EN_ES.put("when", "cuando");
        EN_ES.put("why", "por qué"); EN_ES.put("how", "cómo"); EN_ES.put("what", "qué");
        EN_ES.put("who", "quién"); EN_ES.put("which", "cuál");
        EN_ES.put("in", "en"); EN_ES.put("on", "en"); EN_ES.put("at", "en");
        EN_ES.put("to", "a"); EN_ES.put("for", "para"); EN_ES.put("with", "con");
        EN_ES.put("from", "de"); EN_ES.put("of", "de"); EN_ES.put("by", "por");
        EN_ES.put("about", "sobre"); EN_ES.put("through", "a través de");
        EN_ES.put("during", "durante"); EN_ES.put("before", "antes"); EN_ES.put("after", "después");
        EN_ES.put("between", "entre"); EN_ES.put("under", "bajo"); EN_ES.put("over", "sobre");
        EN_ES.put("without", "sin"); EN_ES.put("within", "dentro de");
        EN_ES.put("and", "y"); EN_ES.put("or", "o"); EN_ES.put("but", "pero");
        EN_ES.put("if", "si"); EN_ES.put("so", "entonces"); EN_ES.put("because", "porque");
        EN_ES.put("although", "aunque"); EN_ES.put("while", "mientras"); EN_ES.put("since", "desde");
        EN_ES.put("until", "hasta"); EN_ES.put("even", "incluso"); EN_ES.put("also", "también");
        EN_ES.put("however", "sin embargo"); EN_ES.put("therefore", "por lo tanto");
        EN_ES.put("then", "entonces"); EN_ES.put("yet", "aún"); EN_ES.put("both", "ambos");
        EN_ES.put("is", "es"); EN_ES.put("are", "son"); EN_ES.put("was", "fue");
        EN_ES.put("were", "eran"); EN_ES.put("be", "ser"); EN_ES.put("been", "sido");
        EN_ES.put("have", "tener"); EN_ES.put("has", "tiene"); EN_ES.put("had", "tuvo");
        EN_ES.put("do", "hacer"); EN_ES.put("does", "hace"); EN_ES.put("did", "hizo");
        EN_ES.put("will", "va a"); EN_ES.put("would", "podría"); EN_ES.put("can", "puede");
        EN_ES.put("could", "podría"); EN_ES.put("should", "debería"); EN_ES.put("must", "debe");
        EN_ES.put("get", "conseguir"); EN_ES.put("make", "hacer"); EN_ES.put("go", "ir");
        EN_ES.put("come", "venir"); EN_ES.put("see", "ver"); EN_ES.put("know", "saber");
        EN_ES.put("think", "pensar"); EN_ES.put("take", "tomar"); EN_ES.put("give", "dar");
        EN_ES.put("find", "encontrar"); EN_ES.put("tell", "decir"); EN_ES.put("work", "trabajar");
        EN_ES.put("call", "llamar"); EN_ES.put("try", "intentar"); EN_ES.put("ask", "preguntar");
        EN_ES.put("need", "necesitar"); EN_ES.put("feel", "sentir"); EN_ES.put("leave", "dejar");
        EN_ES.put("put", "poner"); EN_ES.put("mean", "significar"); EN_ES.put("keep", "mantener");
        EN_ES.put("let", "dejar"); EN_ES.put("begin", "comenzar"); EN_ES.put("show", "mostrar");
        EN_ES.put("hear", "escuchar"); EN_ES.put("play", "jugar"); EN_ES.put("run", "correr");
        EN_ES.put("move", "mover"); EN_ES.put("live", "vivir"); EN_ES.put("believe", "creer");
        EN_ES.put("bring", "traer"); EN_ES.put("happen", "suceder"); EN_ES.put("write", "escribir");
        EN_ES.put("read", "leer"); EN_ES.put("lose", "perder"); EN_ES.put("pay", "pagar");
        EN_ES.put("meet", "reunirse"); EN_ES.put("include", "incluir"); EN_ES.put("continue", "continuar");
        EN_ES.put("learn", "aprender"); EN_ES.put("change", "cambiar"); EN_ES.put("understand", "entender");
        EN_ES.put("watch", "mirar"); EN_ES.put("follow", "seguir"); EN_ES.put("stop", "parar");
        EN_ES.put("create", "crear"); EN_ES.put("speak", "hablar"); EN_ES.put("grow", "crecer");
        EN_ES.put("open", "abrir"); EN_ES.put("win", "ganar"); EN_ES.put("remember", "recordar");
        EN_ES.put("consider", "considerar"); EN_ES.put("buy", "comprar"); EN_ES.put("wait", "esperar");
        EN_ES.put("send", "enviar"); EN_ES.put("receive", "recibir"); EN_ES.put("use", "usar");
        EN_ES.put("build", "construir"); EN_ES.put("good", "bueno"); EN_ES.put("bad", "malo");
        EN_ES.put("big", "grande"); EN_ES.put("small", "pequeño"); EN_ES.put("great", "genial");
        EN_ES.put("high", "alto"); EN_ES.put("low", "bajo"); EN_ES.put("long", "largo");
        EN_ES.put("short", "corto"); EN_ES.put("old", "viejo"); EN_ES.put("new", "nuevo");
        EN_ES.put("first", "primero"); EN_ES.put("last", "último"); EN_ES.put("next", "siguiente");
        EN_ES.put("same", "mismo"); EN_ES.put("different", "diferente"); EN_ES.put("important", "importante");
        EN_ES.put("possible", "posible"); EN_ES.put("real", "real"); EN_ES.put("true", "verdadero");
        EN_ES.put("right", "correcto"); EN_ES.put("wrong", "incorrecto"); EN_ES.put("easy", "fácil");
        EN_ES.put("hard", "difícil"); EN_ES.put("fast", "rápido"); EN_ES.put("slow", "lento");
        EN_ES.put("sure", "seguro"); EN_ES.put("ready", "listo"); EN_ES.put("early", "temprano");
        EN_ES.put("late", "tarde"); EN_ES.put("main", "principal"); EN_ES.put("public", "público");
        EN_ES.put("social", "social"); EN_ES.put("human", "humano"); EN_ES.put("general", "general");
        EN_ES.put("specific", "específico"); EN_ES.put("current", "actual"); EN_ES.put("basic", "básico");
        EN_ES.put("special", "especial"); EN_ES.put("major", "mayor"); EN_ES.put("modern", "moderno");
        EN_ES.put("people", "gente"); EN_ES.put("person", "persona"); EN_ES.put("man", "hombre");
        EN_ES.put("woman", "mujer"); EN_ES.put("child", "niño"); EN_ES.put("children", "niños");
        EN_ES.put("time", "tiempo"); EN_ES.put("year", "año"); EN_ES.put("day", "día");
        EN_ES.put("week", "semana"); EN_ES.put("month", "mes"); EN_ES.put("hour", "hora");
        EN_ES.put("world", "mundo"); EN_ES.put("life", "vida"); EN_ES.put("part", "parte");
        EN_ES.put("place", "lugar"); EN_ES.put("company", "empresa"); EN_ES.put("system", "sistema");
        EN_ES.put("government", "gobierno"); EN_ES.put("country", "país"); EN_ES.put("city", "ciudad");
        EN_ES.put("number", "número"); EN_ES.put("way", "manera"); EN_ES.put("fact", "hecho");
        EN_ES.put("water", "agua"); EN_ES.put("money", "dinero"); EN_ES.put("home", "hogar");
        EN_ES.put("family", "familia"); EN_ES.put("school", "escuela"); EN_ES.put("group", "grupo");
        EN_ES.put("problem", "problema"); EN_ES.put("point", "punto"); EN_ES.put("idea", "idea");
        EN_ES.put("question", "pregunta"); EN_ES.put("answer", "respuesta"); EN_ES.put("result", "resultado");
        EN_ES.put("health", "salud"); EN_ES.put("business", "negocio"); EN_ES.put("data", "datos");
        EN_ES.put("information", "información"); EN_ES.put("technology", "tecnología");
        EN_ES.put("research", "investigación"); EN_ES.put("service", "servicio");
        EN_ES.put("product", "producto"); EN_ES.put("market", "mercado"); EN_ES.put("community", "comunidad");
        EN_ES.put("science", "ciencia"); EN_ES.put("education", "educación"); EN_ES.put("history", "historia");
        EN_ES.put("art", "arte"); EN_ES.put("music", "música"); EN_ES.put("book", "libro");
        EN_ES.put("team", "equipo"); EN_ES.put("food", "comida"); EN_ES.put("energy", "energía");
        EN_ES.put("power", "poder"); EN_ES.put("network", "red"); EN_ES.put("model", "modelo");
        EN_ES.put("level", "nivel"); EN_ES.put("strategy", "estrategia"); EN_ES.put("value", "valor");
        EN_ES.put("impact", "impacto"); EN_ES.put("example", "ejemplo"); EN_ES.put("reason", "razón");
        EN_ES.put("goal", "objetivo"); EN_ES.put("action", "acción"); EN_ES.put("opportunity", "oportunidad");
        EN_ES.put("challenge", "desafío"); EN_ES.put("experience", "experiencia");
        EN_ES.put("knowledge", "conocimiento"); EN_ES.put("situation", "situación");
        EN_ES.put("solution", "solución"); EN_ES.put("source", "fuente"); EN_ES.put("choice", "opción");
        EN_ES.put("decision", "decisión"); EN_ES.put("effect", "efecto"); EN_ES.put("cost", "costo");
        EN_ES.put("success", "éxito"); EN_ES.put("failure", "fracaso"); EN_ES.put("opinion", "opinión");
        EN_ES.put("topic", "tema"); EN_ES.put("not", "no"); EN_ES.put("very", "muy");
        EN_ES.put("more", "más"); EN_ES.put("most", "más"); EN_ES.put("less", "menos");
        EN_ES.put("just", "solo"); EN_ES.put("now", "ahora"); EN_ES.put("already", "ya");
        EN_ES.put("still", "todavía"); EN_ES.put("never", "nunca"); EN_ES.put("always", "siempre");
        EN_ES.put("sometimes", "a veces"); EN_ES.put("maybe", "quizás"); EN_ES.put("probably", "probablemente");
        EN_ES.put("actually", "en realidad"); EN_ES.put("really", "realmente"); EN_ES.put("only", "solo");
        EN_ES.put("well", "bien"); EN_ES.put("much", "mucho"); EN_ES.put("many", "muchos");
        EN_ES.put("few", "pocos"); EN_ES.put("enough", "suficiente"); EN_ES.put("almost", "casi");
        EN_ES.put("quite", "bastante"); EN_ES.put("too", "también"); EN_ES.put("together", "juntos");
        EN_ES.put("again", "otra vez"); EN_ES.put("simply", "simplemente"); EN_ES.put("especially", "especialmente");
        EN_ES.put("certainly", "ciertamente"); EN_ES.put("clearly", "claramente");
        EN_ES.put("finally", "finalmente"); EN_ES.put("recently", "recientemente");
        EN_ES.put("quickly", "rápidamente"); EN_ES.put("easily", "fácilmente");
        EN_ES.put("completely", "completamente"); EN_ES.put("exactly", "exactamente");
        EN_ES.put("approximately", "aproximadamente"); EN_ES.put("primarily", "principalmente");
        EN_ES.put("one", "uno"); EN_ES.put("two", "dos"); EN_ES.put("three", "tres");
        EN_ES.put("four", "cuatro"); EN_ES.put("five", "cinco"); EN_ES.put("six", "seis");
        EN_ES.put("seven", "siete"); EN_ES.put("eight", "ocho"); EN_ES.put("nine", "nueve");
        EN_ES.put("ten", "diez"); EN_ES.put("hundred", "cien"); EN_ES.put("thousand", "mil");
        EN_ES.put("million", "millón");
    }

    public OfflineTranslator(Context context) {
        this.context = context;
    }

    public String translate(String text, String sourceLang) {
        if (text == null || text.trim().isEmpty()) return "";
        String lower = text.toLowerCase().trim();
        if (isSpanish(lower)) return text;
        if ("auto".equals(sourceLang) || sourceLang.startsWith("en")) {
            return translateEnToEs(text, lower);
        }
        return "[" + getLangName(sourceLang) + "] " + text;
    }

    private String translateEnToEs(String original, String lower) {
        for (Map.Entry<String, String> entry : PHRASES_EN_ES.entrySet()) {
            if (lower.equals(entry.getKey()) ||
                lower.equals(entry.getKey() + ".") ||
                lower.equals(entry.getKey() + "?") ||
                lower.equals(entry.getKey() + "!")) {
                return capitalize(entry.getValue());
            }
        }
        String[] words = original.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            String clean = word.toLowerCase().replaceAll("[^a-záéíóúüñ'\\-]", "");
            String punctuation = word.replaceAll("[a-záéíóúüñ'\\-]", "");
            String translated = EN_ES.get(clean);
            if (translated != null) {
                result.append(translated);
            } else {
                String stem = tryStem(clean);
                result.append(stem != null ? stem : word);
            }
            result.append(punctuation).append(" ");
        }
        return capitalize(result.toString().trim());
    }

    private String tryStem(String word) {
        if (word.endsWith("ing") && word.length() > 5) {
            String t = EN_ES.get(word.substring(0, word.length() - 3));
            if (t != null) return t + "ndo";
        }
        if (word.endsWith("ed") && word.length() > 4) {
            String t = EN_ES.get(word.substring(0, word.length() - 2));
            if (t != null) return t;
        }
        if (word.endsWith("s") && word.length() > 3) {
            String t = EN_ES.get(word.substring(0, word.length() - 1));
            if (t != null) return t + "s";
        }
        if (word.endsWith("ly") && word.length() > 4) {
            String t = EN_ES.get(word.substring(0, word.length() - 2));
            if (t != null) return t + "mente";
        }
        return null;
    }

    private boolean isSpanish(String text) {
        String[] markers = {" que ", " de ", " la ", " el ", " en ", " un ", " una ",
                " con ", " por ", " para ", " pero ", " como ", "ción", "ñ", "¿", "¡"};
        int count = 0;
        for (String m : markers) if (text.contains(m)) count++;
        return count >= 2;
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String getLangName(String code) {
        if (code == null) return "?";
        if (code.startsWith("fr")) return "FR";
        if (code.startsWith("de")) return "DE";
        if (code.startsWith("it")) return "IT";
        if (code.startsWith("pt")) return "PT";
        if (code.startsWith("ja")) return "JA";
        if (code.startsWith("ko")) return "KO";
        if (code.startsWith("zh")) return "ZH";
        if (code.startsWith("ru")) return "RU";
        if (code.startsWith("ar")) return "AR";
        return code.substring(0, Math.min(2, code.length())).toUpperCase();
    }

    public void destroy() {}
  }
