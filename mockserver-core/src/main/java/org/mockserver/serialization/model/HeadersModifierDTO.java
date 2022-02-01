package org.mockserver.serialization.model;

import org.mockserver.model.Headers;
import org.mockserver.model.HeadersModifier;

public class HeadersModifierDTO extends KeysToMultiValuesModifierDTO<Headers, HeadersModifier, HeadersModifierDTO> {

    public HeadersModifierDTO() {
    }

    public HeadersModifierDTO(HeadersModifier headersModifier) {
        super(headersModifier);
    }

    @Override
    HeadersModifier newKeysToMultiValuesModifier() {
        return new HeadersModifier();
    }
}
