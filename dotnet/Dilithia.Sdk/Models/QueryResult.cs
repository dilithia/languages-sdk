using System.Text.Json;

namespace Dilithia.Sdk.Models;

/// <summary>
/// The result of a read-only contract query.
/// </summary>
/// <param name="Value">The query return value (as a JSON element).</param>
public record QueryResult(JsonElement? Value);
