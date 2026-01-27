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
import walkingkooka.collect.list.Lists;
import walkingkooka.environment.AuditInfo;
import walkingkooka.environment.ReadOnlyEnvironmentValueException;
import walkingkooka.net.IpPort;
import walkingkooka.net.email.EmailAddress;
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;
import walkingkooka.spreadsheet.SpreadsheetContext;
import walkingkooka.spreadsheet.convert.SpreadsheetConverterContexts;
import walkingkooka.spreadsheet.engine.SpreadsheetEngine;
import walkingkooka.spreadsheet.engine.SpreadsheetEngineContext;
import walkingkooka.spreadsheet.expression.SpreadsheetExpressionEvaluationContext;
import walkingkooka.spreadsheet.formula.SpreadsheetFormula;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadata;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataPropertyName;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataTesting;
import walkingkooka.spreadsheet.reference.SpreadsheetExpressionReferenceLoaders;
import walkingkooka.spreadsheet.reference.SpreadsheetSelection;
import walkingkooka.spreadsheet.server.SpreadsheetServerContext;
import walkingkooka.spreadsheet.value.SpreadsheetCell;
import walkingkooka.storage.StoragePath;
import walkingkooka.storage.StorageValueInfo;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

public final class JettyHttpServerSpreadsheetHttpServerTest implements ClassTesting2<JettyHttpServerSpreadsheetHttpServer>,
    SpreadsheetMetadataTesting {

    @Test
    public void testMetadataCreateTemplate() {
        final SpreadsheetMetadata metadata = JettyHttpServerSpreadsheetHttpServer.with(
            SERVER_URL,
            IpPort.with(2000), // sshdPort
            INDENTATION,
            LINE_ENDING,
            LOCALE,
            (u) -> {
                throw new UnsupportedOperationException();
            },
            Optional.of(
                EmailAddress.parse("default-user@example.com")
            ),
            HAS_NOW
        ).metadataCreateTemplate();

        metadata.spreadsheetConverterContext(
            SpreadsheetMetadata.NO_CELL,
            SpreadsheetConverterContexts.NO_VALIDATION_REFERENCE,
            SpreadsheetMetadataPropertyName.FORMULA_CONVERTER,
            INDENTATION,
            SPREADSHEET_LABEL_NAME_RESOLVER,
            LINE_ENDING,
            CONVERTER_PROVIDER,
            LOCALE_CONTEXT,
            PROVIDER_CONTEXT
        );
    }

    @Test
    public void testStorageListSpreadsheets() {
        final JettyHttpServerSpreadsheetHttpServer server = JettyHttpServerSpreadsheetHttpServer.with(
            SERVER_URL,
            IpPort.with(2000), // sshdPort
            INDENTATION,
            LINE_ENDING,
            LOCALE,
            (u) -> {
                throw new UnsupportedOperationException();
            },
            Optional.of(
                EmailAddress.parse("default-user@example.com")
            ),
            HAS_NOW
        );

        final EmailAddress user = EmailAddress.parse("testStorageListSpreadsheets@example.com");

        final SpreadsheetServerContext spreadsheetServerContext = server.getOrCreateSpreadsheetServerContext(
            Optional.of(user)
        );

        final SpreadsheetContext spreadsheetContext = spreadsheetServerContext.createEmptySpreadsheet(
            Optional.of(LOCALE)
        );

        final SpreadsheetEngineContext engineContext = spreadsheetContext.spreadsheetEngineContext();

        this.checkEquals(
            Lists.of(
                StorageValueInfo.with(
                    StoragePath.parse("/spreadsheet/1"),
                    AuditInfo.create(
                        user,
                        HAS_NOW.now()
                    )
                )
            ),
            engineContext.spreadsheetExpressionEvaluationContext(
                SpreadsheetExpressionEvaluationContext.NO_CELL,
                SpreadsheetExpressionReferenceLoaders.empty()
            ).listStorage(
                StoragePath.parse("/spreadsheet"),
                0,
                2
            )
        );
    }

    @Test
    public void testStorageListCellsFails() {
        final LocalDateTime now = LocalDateTime.MIN;

        final JettyHttpServerSpreadsheetHttpServer server = JettyHttpServerSpreadsheetHttpServer.with(
            SERVER_URL,
            IpPort.with(2000), // sshdPort
            INDENTATION,
            LINE_ENDING,
            LOCALE,
            (u) -> {
                throw new UnsupportedOperationException();
            },
            Optional.of(
                EmailAddress.parse("default-user@example.com")
            ),
            HAS_NOW
        );

        final EmailAddress user = EmailAddress.parse("testStorageListCellsFails@example.com");

        final SpreadsheetServerContext spreadsheetServerContext = server.getOrCreateSpreadsheetServerContext(
            Optional.of(user)
        );

        final SpreadsheetContext spreadsheetContext = spreadsheetServerContext.createEmptySpreadsheet(
            Optional.of(LOCALE)
        );

        final SpreadsheetEngine engine = spreadsheetContext.spreadsheetEngine();
        final SpreadsheetEngineContext engineContext = spreadsheetContext.spreadsheetEngineContext();

        final SpreadsheetCell cell = engine.saveCell(
                SpreadsheetSelection.A1.setFormula(
                    SpreadsheetFormula.EMPTY.setText("=1+2")
                ),
                engineContext
            ).cells()
            .iterator()
            .next();

        assertThrows(
            ReadOnlyEnvironmentValueException.class,
            () -> this.checkEquals(
                Lists.of(
                    StorageValueInfo.with(
                        StoragePath.parse("/spreadsheet/1/cell/"),
                        AuditInfo.create(
                            user,
                            now
                        )
                    )
                ),
                engineContext.spreadsheetExpressionEvaluationContext(
                    SpreadsheetExpressionEvaluationContext.NO_CELL,
                    SpreadsheetExpressionReferenceLoaders.empty()
                ).listStorage(
                    StoragePath.parse("/spreadsheet/1/cell"),
                    0,
                    2
                )
            )
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
