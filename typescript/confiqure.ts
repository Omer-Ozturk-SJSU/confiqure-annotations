/**
 * confiqure — class decorator marking a configuration target.
 *
 * Usage:
 *
 *   import { Confiqure } from "confiqure";
 *
 *   @Confiqure({
 *     end:      "/notifications",
 *     type:     "single",
 *     tools:    ["Amazon_Connect", "Ebay_Connect"],
 *     callback: "https://myapp.com/webhooks/confiqure",
 *   })
 *   class Notifications { /* ... */ }
 *
 * The confiqure CLI scans source files for `@Confiqure`. The backend AI
 * parses the decorated class and generates the chat playbook.
 *
 * Requires TypeScript 5+ (stage-3 decorators) or experimentalDecorators=true.
 */

export interface ConfiqureOptions {
  end?: string;
  type?: string;
  tools?: string[];
  callback?: string;
}

export interface ConfiqureMetadata {
  end: string;
  type: string;
  tools: string[];
  callback: string;
}

const METADATA_KEY = Symbol.for("confiqure.metadata");

export function Confiqure(options: ConfiqureOptions = {}) {
  const metadata: ConfiqureMetadata = {
    end: options.end ?? "",
    type: options.type ?? "single",
    tools: options.tools ?? [],
    callback: options.callback ?? "",
  };
  return function <T extends new (...args: any[]) => unknown>(target: T): T {
    (target as unknown as Record<symbol, ConfiqureMetadata>)[METADATA_KEY] = metadata;
    return target;
  };
}

export function getConfiqureMetadata(target: unknown): ConfiqureMetadata | undefined {
  if (target == null) return undefined;
  return (target as Record<symbol, ConfiqureMetadata>)[METADATA_KEY];
}
