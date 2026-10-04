// Menjalankan skenario persona ke model SUNGGUHAN:
//   ANTHROPIC_API_KEY=... XNAI_PROVIDER=anthropic node eval/run.mjs [id-skenario ...]
// Mencetak balasan + hasil cek; exit 1 bila ada yang gagal. Biaya: satu panggilan per skenario.
import { readFile } from "node:fs/promises";
import { createProvider } from "../src/providers.mjs";
import { cleanForVoice, detectCrisisInConversation } from "../src/safety.mjs";
import { checkReply } from "./checks.mjs";

const scenarios = JSON.parse(await readFile(new URL("./scenarios.json", import.meta.url), "utf8"));
const only = new Set(process.argv.slice(2));
const provider = createProvider();
let failed = 0;
for (const s of scenarios.filter((x) => only.size === 0 || only.has(x.id))) {
  const history = s.history ?? [];
  const crisis = detectCrisisInConversation(s.message, history);
  if (s.crisis !== undefined && s.crisis !== crisis) {
    failed++;
    console.log(`FAIL ${s.id}: deteksi krisis=${crisis}, harapan=${s.crisis}`);
    continue;
  }
  const reply = cleanForVoice(await provider.chat({ message: s.message, history, thinkMode: "RELAX", ageBand: s.ageBand, localHour: s.localHour ?? null, crisis, context: "", companion: "", reasoning: "" }));
  const result = checkReply(s, reply);
  if (!result.ok) failed++;
  console.log(`${result.ok ? "PASS" : "FAIL"} ${s.id} [${s.ageBand}]\n  > ${s.message}\n  < ${reply.replaceAll("\n", " / ")}${result.ok ? "" : `\n  ! ${result.failures.join("; ")}`}\n`);
}
console.log(failed === 0 ? "SEMUA SKENARIO LULUS" : `${failed} skenario gagal`);
process.exit(failed === 0 ? 0 : 1);
