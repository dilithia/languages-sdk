namespace Dilithia.Sdk.Models;

/// <summary>
/// The cost to register a name in the name service.
/// </summary>
/// <param name="Name">The name being queried.</param>
/// <param name="Cost">The registration cost in raw token units.</param>
/// <param name="Duration">The registration duration in seconds.</param>
public record RegistrationCost(string Name, long Cost, long Duration);
