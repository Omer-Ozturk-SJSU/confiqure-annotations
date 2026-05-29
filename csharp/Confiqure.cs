using System;

namespace Confiqure;

/// <summary>
/// Marks a class as a confiqure.ai configuration target.
///
/// <example>
/// <code>
/// [Confiqure(
///     End      = "/notifications",
///     Type     = "single",
///     Tools    = new[] { "Amazon_Connect", "Ebay_Connect" },
///     Callback = "https://myapp.com/webhooks/confiqure"
/// )]
/// public class Notifications { /* ... */ }
/// </code>
/// </example>
///
/// The confiqure CLI scans source files for <c>[Confiqure(...)]</c>. The backend
/// AI parses the annotated class and generates the chat playbook.
/// </summary>
[AttributeUsage(AttributeTargets.Class, AllowMultiple = false, Inherited = false)]
public sealed class ConfiqureAttribute : Attribute
{
    /// <summary>Chat endpoint segment. Defaults to snake_case of the class name when blank.</summary>
    public string End { get; set; } = "";

    /// <summary>Configuration shape. Defaults to "single".</summary>
    public string Type { get; set; } = "single";

    /// <summary>Names of workspace tools referenced during chat.</summary>
    public string[] Tools { get; set; } = Array.Empty<string>();

    /// <summary>End-of-chat callback URL. Defaults to the workspace's defaultCallbackUrl when blank.</summary>
    public string Callback { get; set; } = "";

    public ConfiqureAttribute() { }
}
