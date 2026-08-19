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
import walkingkooka.currency.CurrencyExchangeRaters;
import walkingkooka.environment.AuditInfo;
import walkingkooka.environment.ReadOnlyEnvironmentValueException;
import walkingkooka.net.IpPort;
import walkingkooka.net.header.MediaType;
import walkingkooka.net.http.server.HttpHandler;
import walkingkooka.net.http.server.HttpHandlers;
import walkingkooka.props.Properties;
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
import walkingkooka.storage.StorageContextTesting;
import walkingkooka.storage.StoragePath;
import walkingkooka.storage.StoragePathList;
import walkingkooka.storage.StorageValue;
import walkingkooka.storage.StorageValueInfo;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

public final class JettyHttpServerSpreadsheetHttpServerTest implements ClassTesting2<JettyHttpServerSpreadsheetHttpServer>,
    SpreadsheetMetadataTesting,
    StorageContextTesting {

    private final static HttpHandler<SpreadsheetServerContext> PUBLIC_HTTP_HANDLER = HttpHandlers.fake();

    @Test
    public void testSpreadsheetMetadataCreatorWithoutLocale() {
        final SpreadsheetServerContext spreadsheetServerContext = this.startServerAndSpreadsheetServerContext();

        final SpreadsheetMetadata metadata = spreadsheetServerContext.createMetadata(
            USER,
            Optional.empty()
        );

        metadata.spreadsheetConverterContext(
            SpreadsheetMetadata.NO_CELL,
            SpreadsheetConverterContexts.NO_VALIDATION_REFERENCE,
            SpreadsheetMetadataPropertyName.FORMULA_CONVERTER,
            HAS_USER_DIRECTORIES,
            SPREADSHEET_LABEL_NAME_RESOLVER,
            MEDIA_TYPE_DETECTOR,
            MULTIPLIER,
            SPREADSHEET_METADATA_LOADER,
            CONVERTER_PROVIDER,
            BINARY_TEXT_CONTEXT,
            CURRENCY_LOCALE_CONTEXT,
            PROVIDER_CONTEXT
        );
    }

    @Test
    public void testSpreadsheetMetadataCreatorWithDifferentLocale() {
        final SpreadsheetServerContext spreadsheetServerContext = this.startServerAndSpreadsheetServerContext();

        final SpreadsheetMetadata metadata = spreadsheetServerContext.createMetadata(
            USER,
            Optional.of(DIFFERENT_LOCALE)
        );

        metadata.spreadsheetConverterContext(
            SpreadsheetMetadata.NO_CELL,
            SpreadsheetConverterContexts.NO_VALIDATION_REFERENCE,
            SpreadsheetMetadataPropertyName.FORMULA_CONVERTER,
            HAS_USER_DIRECTORIES,
            SPREADSHEET_LABEL_NAME_RESOLVER,
            MEDIA_TYPE_DETECTOR,
            MULTIPLIER,
            SPREADSHEET_METADATA_LOADER,
            CONVERTER_PROVIDER,
            BINARY_TEXT_CONTEXT,
            CURRENCY_LOCALE_CONTEXT,
            PROVIDER_CONTEXT
        );
    }

    @Test
    public void testStorageCurrentWorkingDirectory() {
        final SpreadsheetServerContext spreadsheetServerContext = this.startServerAndSpreadsheetServerContext();

        final SpreadsheetContext spreadsheetContext = spreadsheetServerContext.createEmptySpreadsheet(OPTIONAL_LOCALE);

        final SpreadsheetEngineContext engineContext = spreadsheetContext.spreadsheetEngineContext();

        final StorageValue storageValue = StorageValue.with(
            StoragePath.parse(
                "/value111.txt"
            )
        ).setValue(
            Optional.of("HelloWorld111")
        );

        this.saveStorageAndCheck(
            engineContext,
            storageValue,
            storageValue
        );

        this.loadStorageAndCheck(
            engineContext,
            storageValue.path(),
            storageValue
        );

        final StoragePath homeStoragePath = StoragePath.parse(
            StoragePath.CURRENT_WORKING_DIRECTORY_PREFIX + "/value111.txt"
        );

        final StorageValue homeStorageValue = storageValue.setPath(homeStoragePath);

        this.loadStorageAndCheck(
            engineContext,
            homeStoragePath,
            homeStorageValue
        );
    }

    @Test
    public void testStorageEnv() {
        final SpreadsheetServerContext spreadsheetServerContext = this.startServerAndSpreadsheetServerContext();

        final SpreadsheetContext spreadsheetContext = spreadsheetServerContext.createEmptySpreadsheet(OPTIONAL_LOCALE);

        final SpreadsheetEngineContext engineContext = spreadsheetContext.spreadsheetEngineContext();

        final StoragePath path = StoragePath.parse(
            StoragePath.ENV_PREFIX + "/locale"
        );

        this.loadStorageAndCheck(
            engineContext,
            path,
            StorageValue.with(path)
                .setValue(
                    Optional.of(
                        Locale.forLanguageTag("en-AU")
                    )
                )
        );
    }

    @Test
    public void testStorageHomeDirectory() {
        final SpreadsheetServerContext spreadsheetServerContext = this.startServerAndSpreadsheetServerContext();

        final SpreadsheetContext spreadsheetContext = spreadsheetServerContext.createEmptySpreadsheet(OPTIONAL_LOCALE);

        final SpreadsheetEngineContext engineContext = spreadsheetContext.spreadsheetEngineContext();

        final StorageValue storageValue = StorageValue.with(
            StoragePath.parse(
                StoragePath.USERS_DIRECTORY_PREFIX + "/" + DIFFERENT_USER.value() + "/value111.txt"
            )
        ).setValue(
            Optional.of("HelloWorld111")
        );

        this.saveStorageAndCheck(
            engineContext,
            storageValue,
            storageValue
        );

        this.loadStorageAndCheck(
            engineContext,
            storageValue.path(),
            storageValue
        );

        final StoragePath homeStoragePath = StoragePath.parse(
            StoragePath.HOME_DIRECTORY_PREFIX + "/value111.txt"
        );

        final StorageValue homeStorageValue = storageValue.setPath(homeStoragePath);

        this.loadStorageAndCheck(
            engineContext,
            homeStoragePath,
            homeStorageValue
        );
    }

    /**
     * <pre>
     * StoragePathList
     *  /
     *  /cwd
     *  /env
     *  /home
     *  /mount-point-paths
     *  /samples
     * </pre>
     */
    @Test
    public void testStorageMountPointPaths() {
        final SpreadsheetServerContext spreadsheetServerContext = this.startServerAndSpreadsheetServerContext();

        final SpreadsheetContext spreadsheetContext = spreadsheetServerContext.createEmptySpreadsheet(OPTIONAL_LOCALE);

        final SpreadsheetEngineContext engineContext = spreadsheetContext.spreadsheetEngineContext();

        this.loadStorageAndCheck(
            engineContext,
            StoragePath.MOUNT_POINT_PATHS,
            StorageValue.with(StoragePath.MOUNT_POINT_PATHS)
                .setValue(
                    Optional.of(
                        StoragePathList.EMPTY.setElements(
                            Lists.of(
                                StoragePath.ROOT,
                                StoragePath.CURRENT_WORKING_DIRECTORY_PREFIX,
                                StoragePath.ENV_PREFIX,
                                StoragePath.HOME_DIRECTORY_PREFIX,
                                StoragePath.MOUNT_POINT_PATHS,
                                StoragePath.parse("/samples")
                            )
                        )
                    )
                )
        );
    }

    @Test
    public void testStorageSamples() {
        final SpreadsheetServerContext spreadsheetServerContext = this.startServerAndSpreadsheetServerContext();

        final SpreadsheetContext spreadsheetContext = spreadsheetServerContext.createEmptySpreadsheet(OPTIONAL_LOCALE);

        final SpreadsheetEngineContext engineContext = spreadsheetContext.spreadsheetEngineContext();

        final StoragePath storagePath = StoragePath.parse("/samples/CurrencyExchange.properties");

        final Properties properties = Properties.parse(
            "AUD-NZD=0.9\n" +
                "AUD-USD=1.5\n" +
                "NZD-AUD=1.1\n" +
                "USD-AUD=0.7\n"
        );

        CurrencyExchangeRaters.properties(
            properties,
            Double::parseDouble
        );

        this.loadStorageAndCheck(
            engineContext,
            storagePath,
            StorageValue.with(storagePath)
                .setValue(
                    Optional.of(properties)
                ).setContentType(
                    Optional.of(MediaType.TEXT_PROPERTIES)
                )
        );
    }

    @Test
    public void testStorageListSpreadsheets() {
        final SpreadsheetServerContext spreadsheetServerContext = this.startServerAndSpreadsheetServerContext();

        final SpreadsheetContext spreadsheetContext = spreadsheetServerContext.createEmptySpreadsheet(OPTIONAL_LOCALE);

        spreadsheetContext.setCurrentWorkingDirectory(OPTIONAL_CURRENT_WORKING_DIRECTORY);
        spreadsheetContext.setHomeDirectory(OPTIONAL_HOME_DIRECTORY);

        final SpreadsheetEngineContext engineContext = spreadsheetContext.spreadsheetEngineContext();

        this.listStorageAndCheck(
            engineContext,
            StoragePath.parse("/spreadsheet"),
            0,
            2,
            StorageValueInfo.with(
                StoragePath.parse("/spreadsheet/1"),
                AuditInfo.create(
                    DIFFERENT_USER,
                    NOW
                )
            )
        );
    }

    @Test
    public void testStorageListCellsFails() {
        final SpreadsheetServerContext spreadsheetServerContext = this.startServerAndSpreadsheetServerContext();
        final SpreadsheetContext spreadsheetContext = spreadsheetServerContext.createEmptySpreadsheet(OPTIONAL_LOCALE);

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
                            DIFFERENT_USER,
                            NOW
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

    private SpreadsheetServerContext startServerAndSpreadsheetServerContext() {
        final JettyHttpServerSpreadsheetHttpServer server = JettyHttpServerSpreadsheetHttpServer.with(
            CHARSET,
            SERVER_URL,
            IpPort.with(2000), // sshdPort
            CURRENCY,
            INDENTATION,
            LINE_ENDING,
            LOCALE,
            PUBLIC_HTTP_HANDLER,
            OPTIONAL_USER,
            HAS_NOW
        );

        return server.getOrCreateSpreadsheetServerContext(
            Optional.of(DIFFERENT_USER)
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
