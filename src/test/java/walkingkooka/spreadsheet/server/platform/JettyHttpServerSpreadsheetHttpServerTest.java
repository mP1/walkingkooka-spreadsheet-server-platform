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
import walkingkooka.reflect.JavaVisibility;
import walkingkooka.reflect.PublicStaticHelperTesting;
import walkingkooka.spreadsheet.convert.SpreadsheetConverterContexts;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadata;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataPropertyName;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataTesting;

import java.lang.reflect.Method;
import java.util.Locale;

public final class JettyHttpServerSpreadsheetHttpServerTest implements PublicStaticHelperTesting<JettyHttpServerSpreadsheetHttpServer>,
    SpreadsheetMetadataTesting {

    @Test
    public void testPrepareMetadataCreateTemplate() {
        final SpreadsheetMetadata metadata = JettyHttpServerSpreadsheetHttpServer.prepareMetadataCreateTemplate(
            Locale.FRENCH
        );

        metadata.spreadsheetConverterContext(
            SpreadsheetConverterContexts.NO_VALIDATION_REFERENCE,
            SpreadsheetMetadataPropertyName.FORMULA_CONVERTER,
            SPREADSHEET_LABEL_NAME_RESOLVER,
            CONVERTER_PROVIDER,
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

    @Override
    public boolean canHavePublicTypes(final Method method) {
        return false;
    }
}
