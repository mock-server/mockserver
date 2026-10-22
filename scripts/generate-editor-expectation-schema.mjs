#!/usr/bin/env node
// Generate a single self-contained JSON Schema for MockServer expectation files
// (`*.mockserver.json`) and write it into the VS Code and JetBrains extensions.
//
// WHY: the editor extensions validate expectation files against the SAME schema
// the server validates against, so the editor is never stricter or laxer than
// MockServer itself. The server keeps the schema as ~45 cross-referencing files
// under mockserver-core and assembles them at runtime
// (org.mockserver.validator.jsonschema.JsonSchemaValidator#addReferencesIntoSchema).
// An editor needs ONE self-contained document, so this script performs the same
// assembly ahead of time and bundles the result.
//
// This is the authoritative source for the published schema — do NOT hand-edit
// the generated files. Re-run after changing any mockserver-core schema:
//   node scripts/generate-editor-expectation-schema.mjs
//
// The reference-file list below mirrors JsonSchemaExpectationValidator exactly.
// If that Java list changes, update this list — the self-check at the end fails
// the build if any `#/definitions/X` reference is left unresolved.

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const SCHEMA_DIR = join(
  REPO_ROOT,
  "mockserver/mockserver-core/src/main/resources/org/mockserver/model/schema"
);

// Mirrors org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator
// (main schema = "expectation"; the rest are the referenced definition files).
const MAIN = "expectation";
const REFERENCE_FILES = [
  "requestDefinition",
  "openAPIDefinition",
  "binaryRequestDefinition",
  "dnsRequestDefinition",
  "httpRequest",
  "httpResponse",
  "httpTemplate",
  "httpForward",
  "httpClassCallback",
  "httpObjectCallback",
  "httpOverrideForwardedRequest",
  "httpForwardValidateAction",
  "httpForwardWithFallback",
  "httpError",
  "httpSseResponse",
  "httpLlmResponse",
  "httpWebSocketResponse",
  "grpcStreamResponse",
  "grpcBidiResponse",
  "binaryResponse",
  "dnsResponse",
  "dnsRecord",
  "afterAction",
  "captureRule",
  "expectationStep",
  "httpChaosProfile",
  "times",
  "timeToLive",
  "stringOrJsonSchema",
  "body",
  "bodyWithContentType",
  "delay",
  "connectionOptions",
  "keyToMultiValue",
  "keyToValue",
  "socketAddress",
  "protocol",
  "rateLimit",
  "conditionalRequestDefinition",
  "anyOfRequestDefinition",
  "recoverAfter",
  // NOTE: "draft-07" is deliberately NOT bundled. The source schemas reference
  // it via `#/definitions/draft-07` to mean "any valid JSON schema" (for JSON
  // body matchers). Embedding the meta-schema breaks validators two ways: its
  // `$id` collides with the validator's built-in draft-07, and its internal
  // `"$ref": "#"` self-references would re-root to the bundle. We also must NOT
  // rewrite those refs to the remote `http://json-schema.org/draft-07/schema#`
  // URI: IntelliJ fetches a remote subschema `$ref` over the network, which
  // fails offline / behind a TLS proxy and makes it discard the whole schema.
  // Instead we replace them with a permissive INLINE schema below.
];

// Each extension keeps its own copy so it can be packaged into the .vsix / plugin
// .jar without a build-time dependency on mockserver-core's resources.
const OUTPUTS = [
  join(REPO_ROOT, "mockserver-vscode/schemas/mockserver-expectation.schema.json"),
  join(
    REPO_ROOT,
    "mockserver-jetbrains/src/main/resources/schemas/mockserver-expectation.schema.json"
  ),
];

function readSchema(name) {
  return JSON.parse(readFileSync(join(SCHEMA_DIR, `${name}.json`), "utf8"));
}

// Mirror JsonSchemaValidator#addReferencesIntoSchema: collect every reference
// file under `definitions`, and hoist each file's own nested `definitions` up to
// the top level so all `#/definitions/X` references resolve against one document.
const definitions = {};
for (const name of REFERENCE_FILES) {
  const def = readSchema(name);
  definitions[name] = def;
  if (def.definitions && typeof def.definitions === "object") {
    for (const [k, v] of Object.entries(def.definitions)) {
      definitions[k] = v;
    }
  }
}

// The expectation schema is the server's root document; expose it as a named
// definition so the editor schema can accept either a single expectation or an
// array of expectations (the initialization-JSON form).
const expectation = readSchema(MAIN);
// The source expectation.json keeps an empty `definitions: {}` placeholder; its
// real definitions live in the sibling files hoisted above. Fail loudly if that
// ever changes, so nested definitions are not silently dropped here.
if (expectation.definitions && Object.keys(expectation.definitions).length > 0) {
  console.error(
    `expectation.json now declares its own definitions (${Object.keys(expectation.definitions).join(", ")}) — ` +
      "update this generator to hoist them instead of dropping them."
  );
  process.exit(1);
}
delete expectation.definitions; // refs resolve against the bundle's root definitions
definitions[MAIN] = expectation;

const bundled = {
  $schema: "http://json-schema.org/draft-07/schema#",
  $id: "https://www.mock-server.com/schema/mockserver-expectation.schema.json",
  title: "MockServer expectation(s)",
  description:
    "A MockServer expectation, or an array of expectations (initialization JSON). " +
    "Generated from mockserver-core — do not hand-edit.",
  // Accept a single expectation (object) or an array of them (initialization JSON).
  // The object form is inlined here (type/properties/additionalProperties/anyOf)
  // rather than expressed as a root `oneOf` of `$ref`s on purpose: IntelliJ's
  // JSON-schema engine cannot offer completion or validation through a root
  // `oneOf` reached only via `$ref` (it has no concrete branch to suggest
  // properties from), whereas a concrete object root works. VS Code handles both.
  // The object-only keywords (`properties`/`additionalProperties`/`anyOf`) apply
  // only to the object form and `items`/`minItems` only to the array form, so the
  // two forms never interfere; the `anyOf` "must specify an action" branches are
  // all `required`-based, which non-object (array) instances satisfy vacuously.
  type: ["object", "array"],
  properties: expectation.properties,
  additionalProperties: false,
  anyOf: expectation.anyOf,
  items: { $ref: "#/definitions/expectation" },
  minItems: 1,
  definitions,
};

// Replace `#/definitions/draft-07` with a permissive INLINE schema (see NOTE above).
// These refs mark "this field is itself a JSON Schema" (the `jsonSchema` body matcher
// and an OpenAPI parameter `schema`). We must NOT point them at the remote
// `http://json-schema.org/draft-07/schema#` URI: IntelliJ resolves a remote subschema
// `$ref` by actually fetching the URL, which silently fails behind an offline/TLS-proxy
// network and makes IntelliJ discard the whole schema (no completion/validation for the
// file). A permissive inline schema needs no network and keeps the rest of the schema
// working in both editors. Trade-off: the embedded-schema field is no longer
// meta-validated as a nested JSON Schema (a niche feature) — accept any object/boolean.
const EMBEDDED_JSON_SCHEMA = {
  type: ["object", "boolean"],
  description: "An embedded JSON Schema (draft-07); accepted as-is.",
};
function inlineDraft07Refs(node) {
  if (Array.isArray(node)) {
    node.forEach(inlineDraft07Refs);
  } else if (node && typeof node === "object") {
    if (node.$ref === "#/definitions/draft-07") {
      delete node.$ref;
      Object.assign(node, EMBEDDED_JSON_SCHEMA);
      return; // the node is now a concrete schema; nothing to recurse into
    }
    for (const value of Object.values(node)) inlineDraft07Refs(value);
  }
}
inlineDraft07Refs(bundled);

// Self-check: every internal `#/definitions/X` reference must resolve. This is
// the guard that catches drift if the Java reference list changes but this one
// is not updated.
const refs = new Set();
JSON.stringify(bundled, (key, value) => {
  if (key === "$ref" && typeof value === "string" && value.startsWith("#/definitions/")) {
    refs.add(value.slice("#/definitions/".length));
  }
  return value;
});
const missing = [...refs].filter((name) => !(name in definitions));
if (missing.length > 0) {
  console.error(
    `Unresolved schema references (add the source file to REFERENCE_FILES): ${missing.join(", ")}`
  );
  process.exit(1);
}

// Self-check: the root must stay IntelliJ-navigable — a concrete object root with
// an inline `properties` map, NOT a bare `oneOf`/`$ref`. IntelliJ gives no
// completion or validation through a root `oneOf` reached only via `$ref`, so a
// regression to that shape must fail the build rather than ship silently.
if (
  bundled.oneOf ||
  !Array.isArray(bundled.type) ||
  !bundled.type.includes("object") ||
  !bundled.properties ||
  Object.keys(bundled.properties).length === 0
) {
  console.error(
    "Root schema is not IntelliJ-navigable: expected a concrete object root with inline `properties` (no root `oneOf`)."
  );
  process.exit(1);
}

const json = JSON.stringify(bundled, null, 2) + "\n";
for (const out of OUTPUTS) {
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, json);
  console.log(`wrote ${out.replace(REPO_ROOT + "/", "")} (${refs.size} refs resolved)`);
}
