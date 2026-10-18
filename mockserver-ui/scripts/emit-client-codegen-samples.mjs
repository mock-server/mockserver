// Emit kitchen-sink composer client-code for one language as compilable files.
//
// WHY: the dashboard composer's per-language tabs (standardToPython / standardToGo
// / standardToRuby / standardToRust in src/lib/codegen/*.ts) are otherwise only
// verified by TypeScript string-assertion / byte-identity tests. Those pin the
// SHAPE of the emitted snippet but cannot catch:
//   * an emitter bug that produces syntactically invalid code (all four langs), or
//   * a client-API rename the generated code now violates (Go / Rust / C# —
//     statically typed clients that live in this monorepo).
// The generated code would ship broken silently. This script drives the SHARED
// representative composer matrix (../src/lib/codegen/extractParityCases.ts — the
// exact `combos` the per-language byte-identity parity tests use, chosen to cover
// every buildExpectationJson branch and per-language escaping path), plus one edit
// per action type carrying every schema field (../src/lib/codegen/actionFieldCases.ts),
// through the requested language's emitter and writes the output as real source files, so a
// CI step can compile / syntax-check them with that language's toolchain and fail
// on drift. It mirrors scripts/emit-java-codegen-samples.mjs (the Java arm).
//
// MECHANISM: the codegen modules use only erasable TypeScript syntax and have no
// non-erasable imports, so Node's native type-stripping (v22.18+) imports them
// directly — no npm install, no bundler, no vitest. Keep it that way.
//
// FILE LAYOUT is per-language, matching the lightest credible compile unit for
// each toolchain (the CI step scaffolds the go.mod / Cargo.toml around these):
//   python -> <out>/sample_NN_<name>.py          (python -m py_compile)
//   ruby   -> <out>/sample_NN_<name>.rb           (ruby -c)
//   go     -> <out>/sample_NN_<name>/main.go      (go build/vet ./...; one main pkg per dir)
//   rust   -> <out>/src/bin/sample_NN_<name>.rs   (cargo check; one bin per file)
//   csharp -> <out>/sample_NN_<name>/Program.cs   (dotnet build; one top-level-statement
//                                                  program per project, as C# allows one)
//
// USAGE: node scripts/emit-client-codegen-samples.mjs <python|ruby|go|rust|csharp> <out-dir>

import './define-build-constants.mjs';
import { mkdirSync, writeFileSync, rmSync, existsSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { combos } from '../src/lib/codegen/extractParityCases.ts';
import { actionFieldCombos } from '../src/lib/codegen/actionFieldCases.ts';
import { standardToPython } from '../src/lib/codegen/python.ts';
import { standardToGo } from '../src/lib/codegen/go.ts';
import { standardToRuby } from '../src/lib/codegen/ruby.ts';
import { standardToRust } from '../src/lib/codegen/rust.ts';
import { standardToCsharp } from '../src/lib/codegen/csharp.ts';

const LANGS = {
  python: { emit: standardToPython, layout: 'flat', ext: 'py' },
  ruby: { emit: standardToRuby, layout: 'flat', ext: 'rb' },
  go: { emit: standardToGo, layout: 'go-dir', ext: 'go' },
  rust: { emit: standardToRust, layout: 'rust-bin', ext: 'rs' },
  csharp: { emit: standardToCsharp, layout: 'cs-dir', ext: 'cs' },
};

const lang = process.argv[2];
const outArg = process.argv[3];
if (!lang || !LANGS[lang]) {
  console.error(`FATAL: first arg must be one of: ${Object.keys(LANGS).join(', ')} (got ${JSON.stringify(lang)})`);
  process.exit(1);
}
if (!outArg) {
  console.error('FATAL: second arg must be the output directory');
  process.exit(1);
}

const finalOut = resolve(outArg);
const { emit, layout } = LANGS[lang];

// Clear any previous run so a shrunk matrix cannot leave stale samples behind.
if (existsSync(finalOut)) rmSync(finalOut, { recursive: true, force: true });
mkdirSync(finalOut, { recursive: true });

const safe = (s) => s.replace(/[^A-Za-z0-9]+/g, '_');

let n = 0;
for (const c of [...combos, ...actionFieldCombos]) {
  const idx = String(n).padStart(2, '0');
  const name = `sample_${idx}_${safe(c.name)}`;
  let code;
  try {
    code = emit(c.matcher, c.action, c.baseUrl);
  } catch (err) {
    console.error(`FATAL: ${lang} emitter threw on combo '${c.name}': ${err && err.stack ? err.stack : err}`);
    process.exit(1);
  }
  if (typeof code !== 'string' || code.length === 0) {
    console.error(`FATAL: ${lang} emitter produced empty output for combo '${c.name}'`);
    process.exit(1);
  }
  if (layout === 'flat') {
    writeFileSync(join(finalOut, `${name}.${LANGS[lang].ext}`), code.endsWith('\n') ? code : code + '\n');
  } else if (layout === 'go-dir') {
    // Each emitted Go program is a complete `package main`; one per directory so
    // the many func main()s do not collide. `go build ./...` compiles all.
    const dir = join(finalOut, name);
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, 'main.go'), code.endsWith('\n') ? code : code + '\n');
  } else if (layout === 'rust-bin') {
    // Cargo auto-discovers src/bin/*.rs as separate binaries; one emitted
    // fn main() program per file. `cargo check` compiles every bin.
    const binDir = join(finalOut, 'src', 'bin');
    mkdirSync(binDir, { recursive: true });
    writeFileSync(join(binDir, `${name}.rs`), code.endsWith('\n') ? code : code + '\n');
  } else if (layout === 'cs-dir') {
    const dir = join(finalOut, name);
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, 'Program.cs'), code.endsWith('\n') ? code : code + '\n');
  }
  n += 1;
}

console.log(`Emitted ${n} ${lang} codegen sample(s) to ${finalOut}`);
