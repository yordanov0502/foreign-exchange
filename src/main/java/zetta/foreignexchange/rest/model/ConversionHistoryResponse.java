package zetta.foreignexchange.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A page of conversion history, filtered and paginated.")
public record ConversionHistoryResponse(
        @Schema(description = "Conversions matching the filters, newest first.")
        List<ConversionHistoryItemResponse> content,

        @Schema(description = "Zero-based page number returned.", example = "0")
        int page,

        @Schema(description = "Maximum number of items per page.", example = "20")
        int size,

        @Schema(description = "Total number of conversions matching the filters.", example = "3")
        long totalElements,

        @Schema(description = "Total number of pages matching the filters.", example = "1")
        int totalPages) {
}
