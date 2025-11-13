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

import javaemul.internal.annotations.GwtIncompatible;
import walkingkooka.Binary;
import walkingkooka.Either;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.convert.Converters;
import walkingkooka.datetime.HasNow;
import walkingkooka.environment.AuditInfo;
import walkingkooka.environment.EnvironmentContext;
import walkingkooka.environment.EnvironmentContexts;
import walkingkooka.locale.LocaleContexts;
import walkingkooka.net.AbsoluteUrl;
import walkingkooka.net.HostAddress;
import walkingkooka.net.IpPort;
import walkingkooka.net.Url;
import walkingkooka.net.UrlPath;
import walkingkooka.net.UrlScheme;
import walkingkooka.net.email.EmailAddress;
import walkingkooka.net.header.apache.tika.ApacheTikaMediaTypeDetectors;
import walkingkooka.net.http.HttpStatus;
import walkingkooka.net.http.HttpStatusCode;
import walkingkooka.net.http.server.HttpHandler;
import walkingkooka.net.http.server.HttpServer;
import walkingkooka.net.http.server.WebFile;
import walkingkooka.net.http.server.WebFiles;
import walkingkooka.net.http.server.hateos.HateosResourceHandlerContexts;
import walkingkooka.net.http.server.jetty.JettyHttpServer;
import walkingkooka.plugin.JarFileTesting;
import walkingkooka.plugin.PluginArchiveManifest;
import walkingkooka.plugin.PluginName;
import walkingkooka.plugin.ProviderContext;
import walkingkooka.plugin.store.Plugin;
import walkingkooka.plugin.store.PluginStore;
import walkingkooka.plugin.store.PluginStores;
import walkingkooka.reflect.PublicStaticHelper;
import walkingkooka.spreadsheet.SpreadsheetName;
import walkingkooka.spreadsheet.compare.provider.SpreadsheetComparatorProviders;
import walkingkooka.spreadsheet.convert.provider.SpreadsheetConvertersConverterProviders;
import walkingkooka.spreadsheet.engine.SpreadsheetEngineContextMode;
import walkingkooka.spreadsheet.engine.SpreadsheetEngineContexts;
import walkingkooka.spreadsheet.export.provider.SpreadsheetExporterProviders;
import walkingkooka.spreadsheet.expression.SpreadsheetExpressionFunctions;
import walkingkooka.spreadsheet.expression.function.provider.SpreadsheetExpressionFunctionProviders;
import walkingkooka.spreadsheet.format.pattern.SpreadsheetPattern;
import walkingkooka.spreadsheet.format.provider.SpreadsheetFormatterProvider;
import walkingkooka.spreadsheet.format.provider.SpreadsheetFormatterProviders;
import walkingkooka.spreadsheet.format.provider.SpreadsheetFormatterSelector;
import walkingkooka.spreadsheet.importer.provider.SpreadsheetImporterProviders;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadata;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataContexts;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataPropertyName;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataTesting;
import walkingkooka.spreadsheet.meta.store.SpreadsheetMetadataStore;
import walkingkooka.spreadsheet.meta.store.SpreadsheetMetadataStores;
import walkingkooka.spreadsheet.parser.provider.SpreadsheetParserProvider;
import walkingkooka.spreadsheet.parser.provider.SpreadsheetParserProviders;
import walkingkooka.spreadsheet.provider.SpreadsheetProvider;
import walkingkooka.spreadsheet.provider.SpreadsheetProviderContexts;
import walkingkooka.spreadsheet.provider.SpreadsheetProviders;
import walkingkooka.spreadsheet.reference.SpreadsheetSelection;
import walkingkooka.spreadsheet.security.store.SpreadsheetGroupStores;
import walkingkooka.spreadsheet.security.store.SpreadsheetUserStores;
import walkingkooka.spreadsheet.server.SpreadsheetHttpServer;
import walkingkooka.spreadsheet.server.SpreadsheetServerContext;
import walkingkooka.spreadsheet.server.SpreadsheetServerContexts;
import walkingkooka.spreadsheet.store.SpreadsheetCellRangeStores;
import walkingkooka.spreadsheet.store.SpreadsheetCellReferencesStores;
import walkingkooka.spreadsheet.store.SpreadsheetCellStores;
import walkingkooka.spreadsheet.store.SpreadsheetColumnStores;
import walkingkooka.spreadsheet.store.SpreadsheetLabelReferencesStores;
import walkingkooka.spreadsheet.store.SpreadsheetLabelStores;
import walkingkooka.spreadsheet.store.SpreadsheetRowStores;
import walkingkooka.spreadsheet.store.repo.SpreadsheetStoreRepositories;
import walkingkooka.spreadsheet.validation.form.store.SpreadsheetFormStores;
import walkingkooka.storage.Storages;
import walkingkooka.terminal.TerminalContexts;
import walkingkooka.text.CharSequences;
import walkingkooka.text.Indentation;
import walkingkooka.text.LineEnding;
import walkingkooka.tree.expression.ExpressionNumberKind;
import walkingkooka.tree.expression.function.provider.ExpressionFunctionAliasSet;
import walkingkooka.tree.json.marshall.JsonNodeMarshallContexts;
import walkingkooka.tree.json.marshall.JsonNodeMarshallUnmarshallContext;
import walkingkooka.tree.json.marshall.JsonNodeMarshallUnmarshallContexts;
import walkingkooka.tree.json.marshall.JsonNodeUnmarshallContexts;
import walkingkooka.util.SystemProperty;
import walkingkooka.validation.form.provider.FormHandlerProviders;
import walkingkooka.validation.provider.ValidatorProviders;

import java.io.IOException;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Creates a {@link SpreadsheetHttpServer} with memory stores using a Jetty server using the scheme/host/port from cmd line arguments.
 */
public final class JettyHttpServerSpreadsheetHttpServer implements PublicStaticHelper,
    JarFileTesting {

    /**
     * Starts a server on the scheme/host/port passed as arguments, serving files from the current directory.
     */
    public static void main(final String[] args) throws Exception {
        switch (args.length) {
            case 0:
                throw new IllegalArgumentException("Missing serverUrl, defaultLocale, systemUser, file server root for jetty HttpServer");
            case 1:
                throw new IllegalArgumentException("Missing default Locale, systemUser, file server root for jetty HttpServer");
            case 2:
                throw new IllegalArgumentException("Missing system systemUser, file server root for jetty HttpServer");
            case 3:
                throw new IllegalArgumentException("Missing file server root for jetty HttpServer");
            default:
                startJettyHttpServer(
                    serverUrl(args[0]),
                    locale(args[1]),
                    user(args[2]),
                    fileServer(args[3])
                );
                break;
        }
    }

    private final static HasNow HAS_NOW = LocalDateTime::now;

    private static EnvironmentContext environmentContext(final Locale locale,
                                                         final Optional<EmailAddress> user) {
        return EnvironmentContexts.map(
            EnvironmentContexts.empty(
                locale,
                HAS_NOW,
                user
            )
        );
    }

    private static AbsoluteUrl serverUrl(final String string) {
        try {
            return Url.parseAbsolute(string);
        } catch (final IllegalArgumentException cause) {
            System.err.println("Invalid serverUrl: " + cause.getMessage());
            throw cause;
        }
    }

    private static Locale locale(final String string) {
        final Locale defaultLocale;
        try {
            defaultLocale = Locale.forLanguageTag(string);
        } catch (final RuntimeException cause) {
            System.err.println("Invalid default Locale: " + cause.getMessage());
            throw cause;
        }
        return defaultLocale;
    }

    private static Optional<EmailAddress> user(final String string) {
        final EmailAddress emailAddress;

        try {
            emailAddress = EmailAddress.parse(string);
        } catch (final RuntimeException cause) {
            System.err.println("Invalid user: " + cause.getMessage());
            throw cause;
        }
        return Optional.ofNullable(emailAddress);
    }

    /**
     * A factory that takes a string with uris (currently only a file and jar) creating a function that
     * returns a {@link WebFile} for a {@link UrlPath}.
     */
    private static Function<UrlPath, Either<WebFile, HttpStatus>> fileServer(final String string) throws IOException {
        final List<Function<UrlPath, Either<WebFile, HttpStatus>>> fileSystems = Lists.array();

        for (final String uri : string.split(",")) {
            if (uri.startsWith("file://")) {
                fileSystems.add(
                    fileFileServer(uri)
                );
                continue;
            }
            if (uri.startsWith("jar:file://")) {
                fileSystems.add(
                    jarFileServer(uri)
                );
                continue;
            }

            throw new IllegalArgumentException("Unsupported uri: " + CharSequences.quoteAndEscape(uri));
        }

        return (p) -> {
            Either<WebFile, HttpStatus> result = NOT_FOUND;

            for (final Function<UrlPath, Either<WebFile, HttpStatus>> possible : fileSystems) {
                result = possible.apply(p);
                if (result.isLeft()) {
                    break;
                }
            }

            return result;
        };
    }

    /**
     * Creates a function which uses the path within the file uri as the base directory for file requests.
     */
    private static Function<UrlPath, Either<WebFile, HttpStatus>> fileFileServer(final String uri) {
        final String basePath = uri.substring("file://".length());
        if (false == Files.isDirectory(Paths.get(basePath))) {
            throw new IllegalArgumentException("GWT codesrvr files directory not found: " + CharSequences.quoteAndEscape(basePath));
        }

        final String pathSeparator = SystemProperty.FILE_SEPARATOR.requiredPropertyValue();

        final String base = basePath.endsWith(pathSeparator) ?
            basePath :
            basePath + pathSeparator;

        return (p) -> {
            Either<WebFile, HttpStatus> result = NOT_FOUND;

            final String urlPath = p.value();
            final String path = base +
                (urlPath.startsWith("/") ?
                    urlPath.substring(1) :
                    urlPath
                );

            final Path file = Paths.get(path);
            if (Files.isRegularFile(file)) {
                result = Either.left(webFile(file));
            }

            return result;
        };
    }

    /**
     * Creates a function which sources files from within the jar within the file URI.
     */
    private static Function<UrlPath, Either<WebFile, HttpStatus>> jarFileServer(final String uri) throws IOException {
        final int endOfJar = uri.indexOf('!');
        if (-1 == endOfJar) {
            throw new IllegalArgumentException("Missing end of jar file from " + CharSequences.quoteAndEscape(uri));
        }

        final FileSystem fileSystem = FileSystems.newFileSystem(
            Paths.get(
                uri.substring(
                    "jar:file://".length(),
                    endOfJar
                )
            ),
            ClassLoader.getSystemClassLoader()
        );

        final String base = uri.substring(endOfJar + 1);

        return (p) -> {
            Either<WebFile, HttpStatus> result = NOT_FOUND;

            final String path = base + p.value().substring(1);

            final Path file = fileSystem.getPath(path);
            if (Files.isRegularFile(file)) {
                result = Either.left(webFile(file));
            }

            return result;
        };
    }


    private final static Either<WebFile, HttpStatus> NOT_FOUND = Either.right(HttpStatusCode.NOT_FOUND.status());

    private static final JsonNodeMarshallUnmarshallContext JSON_NODE_MARSHALL_UNMARSHALL_CONTEXT = JsonNodeMarshallUnmarshallContexts.basic(
        JsonNodeMarshallContexts.basic(),
        JsonNodeUnmarshallContexts.basic(
            ExpressionNumberKind.DEFAULT,
            MathContext.DECIMAL32
        )
    );

    private static WebFile webFile(final Path file) {
        return WebFiles.file(
            file,
            ApacheTikaMediaTypeDetectors.apacheTika(),
            (b) -> Optional.empty()
        );
    }

    private static void startJettyHttpServer(final AbsoluteUrl serverUrl,
                                             final Locale defaultLocale,
                                             final Optional<EmailAddress> user,
                                             final Function<UrlPath, Either<WebFile, HttpStatus>> fileServer) {
        final SpreadsheetMetadata createMetadataTemplate = prepareMetadataCreateTemplate(defaultLocale);

        final SpreadsheetMetadataStore metadataStore = SpreadsheetMetadataStores.spreadsheetCellStoreAction(
            SpreadsheetMetadataStores.treeMap(),
            (id) -> serverContext.storeRepositoryOrFail(id)
                .cells()
        );

        serverContext = SpreadsheetServerContexts.basic(
            serverUrl,
            () -> SpreadsheetStoreRepositories.basic(
                SpreadsheetCellStores.treeMap(),
                SpreadsheetCellReferencesStores.treeMap(),
                SpreadsheetColumnStores.treeMap(),
                SpreadsheetFormStores.treeMap(),
                SpreadsheetGroupStores.treeMap(),
                SpreadsheetLabelStores.treeMap(),
                SpreadsheetLabelReferencesStores.treeMap(),
                metadataStore,
                SpreadsheetCellRangeStores.treeMap(),
                SpreadsheetRowStores.treeMap(),
                Storages.tree(),
                SpreadsheetUserStores.treeMap()
            ),
            systemSpreadsheetProvider(defaultLocale),
            (c) -> SpreadsheetEngineContexts.basic(
                SpreadsheetEngineContextMode.FORMULA,
                c,
                TerminalContexts.fake()
            ),
            environmentContext(
                defaultLocale,
                user
            ),
            LocaleContexts.jre(defaultLocale),
            SpreadsheetMetadataContexts.basic(
                (final EmailAddress creator,
                 final Optional<Locale> creatorLocale) -> {
                    final LocalDateTime now = HAS_NOW.now();

                    return metadataStore.save(
                        createMetadataTemplate.set(
                            SpreadsheetMetadataPropertyName.AUDIT_INFO,
                            AuditInfo.with(
                                creator,
                                now,
                                creator,
                                now
                            )
                        )
                    );
                },
                metadataStore
            ),
            HateosResourceHandlerContexts.basic(
                Indentation.SPACES2,
                LineEnding.SYSTEM,
                JSON_NODE_MARSHALL_UNMARSHALL_CONTEXT
            ), // final HateosResourceHandlerContext hateosResourceHandlerContext,
            providerContext(
                HAS_NOW,
                defaultLocale,
                user
            )
        );

        final SpreadsheetHttpServer server = SpreadsheetHttpServer.with(
            ApacheTikaMediaTypeDetectors.apacheTika(),
            fileServer,
            jettyHttpServer(
                serverUrl.host(),
                serverUrl.port()
                    .orElse(
                        serverUrl.scheme().equals(UrlScheme.HTTP) ?
                            IpPort.HTTP :
                            IpPort.HTTPS
                    )
            ),
            serverContext
        );

        server.start();
    }

    private static SpreadsheetServerContext serverContext;

    /**
     * Creates a global or system {@link SpreadsheetProvider} which maybe shared given the system {@link Locale}.
     */
    private static SpreadsheetProvider systemSpreadsheetProvider(final Locale locale) {
        final SpreadsheetFormatterProvider spreadsheetFormatterProvider = SpreadsheetFormatterProviders.spreadsheetFormatters();
        final SpreadsheetParserProvider spreadsheetParserProvider = SpreadsheetParserProviders.spreadsheetParsePattern(
            spreadsheetFormatterProvider
        );

        final SpreadsheetMetadata metadata = SpreadsheetMetadata.EMPTY.set(
            SpreadsheetMetadataPropertyName.LOCALE,
            locale
        ).set(
            SpreadsheetMetadataPropertyName.DATE_FORMATTER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.DATE_FORMATTER)
        ).set(
            SpreadsheetMetadataPropertyName.DATE_PARSER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.DATE_PARSER)
        ).set(
            SpreadsheetMetadataPropertyName.DATE_TIME_FORMATTER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.DATE_TIME_FORMATTER)
        ).set(
            SpreadsheetMetadataPropertyName.DATE_TIME_PARSER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.DATE_TIME_PARSER)
        ).set(
            SpreadsheetMetadataPropertyName.ERROR_FORMATTER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.ERROR_FORMATTER)
        ).set(
            SpreadsheetMetadataPropertyName.NUMBER_FORMATTER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.NUMBER_FORMATTER)
        ).set(
            SpreadsheetMetadataPropertyName.NUMBER_PARSER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.NUMBER_PARSER)
        ).set(
            SpreadsheetMetadataPropertyName.TEXT_FORMATTER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.TEXT_FORMATTER)
        ).set(
            SpreadsheetMetadataPropertyName.TIME_FORMATTER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.TIME_FORMATTER)
        ).set(
            SpreadsheetMetadataPropertyName.TIME_PARSER,
            SpreadsheetMetadataTesting.METADATA_EN_AU.getOrFail(SpreadsheetMetadataPropertyName.TIME_PARSER)
        );

        return SpreadsheetProviders.basic(
            SpreadsheetConvertersConverterProviders.spreadsheetConverters(
                (ProviderContext p) ->
                    metadata.dateTimeConverter(
                        spreadsheetFormatterProvider,
                        spreadsheetParserProvider,
                        SpreadsheetMetadataTesting.PROVIDER_CONTEXT
                    )
            ), // converterProvider
            SpreadsheetExpressionFunctionProviders.expressionFunctionProvider(SpreadsheetExpressionFunctions.NAME_CASE_SENSITIVITY),
            SpreadsheetComparatorProviders.spreadsheetComparators(),
            SpreadsheetExporterProviders.spreadsheetExport(),
            spreadsheetFormatterProvider,
            FormHandlerProviders.validation(),
            SpreadsheetImporterProviders.spreadsheetImport(),
            spreadsheetParserProvider,
            ValidatorProviders.validators()
        );
    }

    /**
     * Creates a {@link ProviderContext} with the given {@link EmailAddress user} and {@link Locale}, all other
     * state is common for all users
     */
    private static ProviderContext providerContext(final HasNow hasNow,
                                                   final Locale locale,
                                                   final Optional<EmailAddress> user) {
        final PluginStore pluginStore = PluginStores.treeMap();

        final Map<String, byte[]> fileToContent = Maps.sorted();
        fileToContent.put(
            "dir111/file111.txt",
            "Hello".getBytes(StandardCharsets.UTF_8)
        );
        fileToContent.put(
            "file-2.bin",
            new byte[0]
        );
        for (int i = 3; i < 100; i++) {
            fileToContent.put(
                "file" + i + ".txt",
                "Hello".getBytes(StandardCharsets.UTF_8)
            );
        }

        final byte[] archive = JarFileTesting.jarFile(
            "Manifest-Version: 1.0\r\n" +
                "plugin-provider-factory-className: sample.TestPlugin123\r\n" +
                "plugin-name: test-plugin-123\r\n" +
                "\r\n",
            fileToContent
        );

        // will fail if the manifest is missing required entries.
        PluginArchiveManifest.fromArchive(
            Binary.with(archive)
        );

        pluginStore.save(
            Plugin.with(
                PluginName.with("test-plugin-123"),
                "TestPlugin123-filename.jar", // filename
                Binary.with(archive), // archive
                EmailAddress.parse("plugin-author@example.com"),
                hasNow.now()
            )
        );

        return SpreadsheetProviderContexts.basic(
            pluginStore,
            JSON_NODE_MARSHALL_UNMARSHALL_CONTEXT,
            environmentContext(
                locale,
                user
            ),
            LocaleContexts.jre(locale)
        );
    }

    /**
     * The default name given to all empty spreadsheets
     */
    private final static SpreadsheetName DEFAULT_NAME = SpreadsheetName.with("Untitled");

    private final static ExpressionFunctionAliasSet FUNCTION_ALIASES = SpreadsheetExpressionFunctionProviders.expressionFunctionProvider(SpreadsheetExpressionFunctions.NAME_CASE_SENSITIVITY)
        .expressionFunctionInfos()
        .aliasSet();

    /**
     * Prepares and merges the default and user locale, loading defaults and more.
     */
    static SpreadsheetMetadata prepareMetadataCreateTemplate(final Locale defaultLocale) {
        return SpreadsheetMetadata.EMPTY
            .set(SpreadsheetMetadataPropertyName.SPREADSHEET_NAME, DEFAULT_NAME)
            .set(SpreadsheetMetadataPropertyName.LOCALE, defaultLocale)
            .set(SpreadsheetMetadataPropertyName.VIEWPORT_HOME, SpreadsheetSelection.A1)
            .setDefaults(
                SpreadsheetMetadata.NON_LOCALE_DEFAULTS
                    .set(SpreadsheetMetadataPropertyName.LOCALE, defaultLocale)
                    .loadFromLocale(
                        LocaleContexts.jre(defaultLocale)
                    ).set(
                        SpreadsheetMetadataPropertyName.CELL_CHARACTER_WIDTH,
                        1
                    ).set(
                        SpreadsheetMetadataPropertyName.DATE_TIME_OFFSET,
                        Converters.EXCEL_1900_DATE_SYSTEM_OFFSET
                    ).set(
                        SpreadsheetMetadataPropertyName.DEFAULT_YEAR,
                        1900
                    ).set(
                        SpreadsheetMetadataPropertyName.ERROR_FORMATTER,
                        SpreadsheetFormatterSelector.parse("badge-error default-text")
                    ).set(
                        SpreadsheetMetadataPropertyName.EXPRESSION_NUMBER_KIND,
                        ExpressionNumberKind.DOUBLE
                    ).set(
                        SpreadsheetMetadataPropertyName.FIND_FUNCTIONS,
                        SpreadsheetExpressionFunctionProviders.FIND
                    ).set(
                        SpreadsheetMetadataPropertyName.FORMULA_FUNCTIONS,
                        SpreadsheetExpressionFunctionProviders.FORMULA
                    ).set(
                        SpreadsheetMetadataPropertyName.FORMATTING_FUNCTIONS,
                        SpreadsheetExpressionFunctionProviders.FORMATTING
                    ).set(
                        SpreadsheetMetadataPropertyName.FUNCTIONS,
                        FUNCTION_ALIASES
                    ).set(
                        SpreadsheetMetadataPropertyName.PRECISION,
                        MathContext.DECIMAL32.getPrecision())
                    .set(
                        SpreadsheetMetadataPropertyName.ROUNDING_MODE,
                        RoundingMode.HALF_UP)
                    .set(
                        SpreadsheetMetadataPropertyName.TEXT_FORMATTER,
                        SpreadsheetPattern.DEFAULT_TEXT_FORMAT_PATTERN.spreadsheetFormatterSelector())
                    .set(
                        SpreadsheetMetadataPropertyName.TWO_DIGIT_YEAR,
                        20
                    ).set(
                        SpreadsheetMetadataPropertyName.VALIDATION_FUNCTIONS,
                        SpreadsheetExpressionFunctionProviders.VALIDATION
                    )
            );
    }

    /**
     * Creates a {@link JettyHttpServer} given the given host and port.
     */
    @GwtIncompatible
    private static Function<HttpHandler, HttpServer> jettyHttpServer(final HostAddress host,
                                                                     final IpPort port) {
        return (handler) -> JettyHttpServer.with(host, port, handler);
    }

    /**
     * Stop creation
     */
    private JettyHttpServerSpreadsheetHttpServer() {
        throw new UnsupportedOperationException();
    }
}
