//! `confiqure` — attribute macro marker for configuration targets.
//!
//! Apply `#[confiqure(...)]` to a struct that represents a configuration
//! target. The macro is a no-op pass-through; the confiqure CLI scans source
//! files for the attribute and uploads them. The backend AI parses the
//! annotated struct and generates the chat playbook.
//!
//! ```ignore
//! use confiqure::confiqure;
//!
//! #[confiqure(
//!     end      = "/notifications",
//!     r#type   = "single",
//!     tools    = "Amazon_Connect,Ebay_Connect",
//!     callback = "https://myapp.com/webhooks/confiqure"
//! )]
//! struct Notifications {
//!     /* ... */
//! }
//! ```
//!
//! Note: Rust proc-macro attribute syntax doesn't natively support array
//! literals, so `tools` is a comma-separated string. The backend AI handles
//! splitting.

extern crate proc_macro;

use proc_macro::TokenStream;

#[proc_macro_attribute]
pub fn confiqure(_attr: TokenStream, item: TokenStream) -> TokenStream {
    // Pass-through. The CLI scans source text for `#[confiqure(...)]`;
    // we don't transform the AST here.
    item
}
