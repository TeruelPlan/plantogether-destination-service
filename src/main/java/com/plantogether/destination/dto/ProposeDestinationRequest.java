package com.plantogether.destination.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposeDestinationRequest {

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "^\\S(.*\\S)?$", message = "name must not have leading or trailing whitespace")
    private String name;

    @Size(max = 2000)
    private String description;

    @Size(max = 500)
    private String imageKey;

    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal estimatedBudget;

    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be ISO 4217")
    private String currency;

    @Size(max = 512)
    @Pattern(
            regexp = "^(https?://).+",
            message = "externalUrl must start with http:// or https://")
    private String externalUrl;

    @AssertTrue(message = "estimatedBudget and currency must be provided together")
    public boolean isBudgetAndCurrencyConsistent() {
        return (estimatedBudget == null) == (currency == null);
    }
}
