// Deterministic safety helpers. They never replace the model's judgement; they make sure the
// crisis instructions are always present and that a person in distress always gets *something*
// even when the model or the daily budget is unavailable.

function normalize(text) {
  return String(text ?? "")
    .toLowerCase()
    .replace(/(.)\1{2,}/g, "$1") // "matiiii" -> "mati"
    .replace(/[-_]/g, " ")
    .replace(/\b(ga|gak|nggak|nggk|ngga|enggak|engga|tidak|tak|gk)\b/g, "tdk")
    .replace(/\b(pengen|pingin|pengin|pgn|ingin|pengen banget)\b/g, "mau")
    .replace(/\s+/g, " ");
}

const CRISIS_PATTERNS = [
  /\b(bunuh|membunuh)\s?diri\b/,
  /\bmau\s?(banget\s)?mati\b/,
  /\b(akhiri|mengakhiri|akhirin|ngakhirin)\s?hidup\b/,
  /\btdk\s?mau\s?hidup(\slagi)?\b/,
  /\bcapek\s?hidup\b/,
  /\b(sakiti|menyakiti|nyakitin|nyakiti|lukai|melukai)\s?diri\b/,
  /\b(iris|sayat|gores|silet)\s?(tangan|nadi|lengan|pergelangan)\b/,
  /\blebih\s?baik\s?(gue|aku|saya|gw|w)\s?(mati|tdk\s?ada)\b/,
  /\bself\s?harm\b/,
  /\bsuicid(e|al)\b/,
  /\bkill\s?myself\b/,
  /\bend\s?(my\s?life|it\s?all)\b/,
  /\bwant\s?to\s?die\b/,
];

export function detectCrisis(text) {
  const t = normalize(text);
  return CRISIS_PATTERNS.some((p) => p.test(t));
}

/** Current message, or any of the user's last three turns: "iya" after a check-in must stay in crisis mode. */
export function detectCrisisInConversation(message, history = []) {
  if (detectCrisis(message)) return true;
  const recentUser = history.filter((turn) => turn.role === "user").slice(-3);
  return recentUser.some((turn) => detectCrisis(turn.text));
}

const FALLBACK = {
  ADULT:
    "Gue di sini. Makasih udah cerita, kedengerannya lo lagi di titik yang berat banget. Kalau lo kepikiran mengakhiri hidup atau nyakitin diri, tolong jangan sendirian: hubungi orang terdekat yang lo percaya sekarang, atau layanan darurat 112 kalau udah berbahaya. Gue tetap di sini. Lo aman sekarang?",
  TEEN:
    "Gue di sini. Makasih udah cerita, kedengerannya lagi berat banget. Kalau kamu kepikiran mengakhiri hidup atau nyakitin diri, tolong jangan sendirian: ngobrol sekarang sama orang tua, wali, atau guru yang kamu percaya, atau hubungi layanan darurat 112 kalau udah berbahaya. Itu bukan hal yang memalukan. Kamu aman sekarang?",
};

export function crisisFallback(ageBand) {
  return ageBand === "ADULT" ? FALLBACK.ADULT : FALLBACK.TEEN;
}

/** Replies are often spoken aloud, so strip markdown the model may still emit. */
export function cleanForVoice(text) {
  return String(text ?? "")
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`([^`]*)`/g, "$1")
    .replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, "$1")
    .replace(/\*\*|__/g, "")
    .replace(/(^|\s)\*(?=\S)([^*\n]+?)\*(?=\s|$|[.,!?])/g, "$1$2")
    .replace(/^\s{0,3}#{1,6}\s+/gm, "")
    .replace(/^\s*[-*•]\s+/gm, "")
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}
