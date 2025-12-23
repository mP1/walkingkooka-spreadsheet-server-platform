/*
 * Copyright 2019 Miroslav Pokorny (github.com/mP1)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package walkingkooka.spreadsheet.server.platform;

import org.junit.jupiter.api.Test;
import walkingkooka.net.IpPort;
import walkingkooka.net.email.EmailAddress;
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;
import walkingkooka.spreadsheet.convert.SpreadsheetConverterContexts;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadata;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataPropertyName;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataTesting;
import walkingkooka.text.LineEnding;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

public final class JettyHttpServerSpreadsheetHttpServerTest implements ClassTesting2<JettyHttpServerSpreadsheetHttpServer>,
    SpreadsheetMetadataTesting {

    @Test
    public void testPrepareMetadataCreateTemplate() {
        final SpreadsheetMetadata metadata = JettyHttpServerSpreadsheetHttpServer.with(
            SERVER_URL,
            IpPort.with(2000), // sshdPort
            LineEnding.NL,
            Locale.ENGLISH,
            (u) -> {
                throw new UnsupportedOperationException();
            },
            Optional.of(
                EmailAddress.parse("default-user@example.com")
            ),
            LocalDateTime::now
        ).prepareMetadataCreateTemplate();

        metadata.spreadsheetConverterContext(
            SpreadsheetMetadata.NO_CELL,
            SpreadsheetConverterContexts.NO_VALIDATION_REFERENCE,
            SpreadsheetMetadataPropertyName.FORMULA_CONVERTER,
            SPREADSHEET_LABEL_NAME_RESOLVER,
            CONVERTER_PROVIDER,
            LOCALE_CONTEXT,
            PROVIDER_CONTEXT
        );
    }

    // Class............................................................................................................

    @Override
    public Class<JettyHttpServerSpreadsheetHttpServer> type() {
        return JettyHttpServerSpreadsheetHttpServer.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PUBLIC;
    }
}
