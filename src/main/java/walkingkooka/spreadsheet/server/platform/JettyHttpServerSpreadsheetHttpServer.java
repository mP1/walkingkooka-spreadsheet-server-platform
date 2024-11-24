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
import walkingkooka.Either;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.convert.Converters;
import walkingkooka.environment.EnvironmentContext;
import walkingkooka.net.AbsoluteUrl;
import walkingkooka.net.HostAddress;
import walkingkooka.net.IpPort;
import walkingkooka.net.Url;
import walkingkooka.net.UrlPath;
import walkingkooka.net.UrlScheme;
import walkingkooka.net.header.apache.tika.ApacheTikas;
import walkingkooka.net.http.HttpStatus;
import walkingkooka.net.http.HttpStatusCode;
import walkingkooka.net.http.server.HttpHandler;
import walkingkooka.net.http.server.HttpServer;
import walkingkooka.net.http.server.WebFile;
import walkingkooka.net.http.server.WebFiles;
import walkingkooka.net.http.server.jetty.JettyHttpServer;
import walkingkooka.plugin.ProviderContexts;
import walkingkooka.plugin.store.PluginStores;
import walkingkooka.reflect.PublicStaticHelper;
import walkingkooka.spreadsheet.SpreadsheetExpressionFunctionNames;
import walkingkooka.spreadsheet.SpreadsheetId;
import walkingkooka.spreadsheet.SpreadsheetName;
import walkingkooka.spreadsheet.compare.SpreadsheetComparatorProviders;
import walkingkooka.spreadsheet.convert.SpreadsheetConvertersConverterProviders;
import walkingkooka.spreadsheet.export.SpreadsheetExporterProviders;
import walkingkooka.spreadsheet.expression.function.SpreadsheetExpressionFunctionProviders;
import walkingkooka.spreadsheet.format.SpreadsheetFormatterProvider;
import walkingkooka.spreadsheet.format.SpreadsheetFormatterProviders;
import walkingkooka.spreadsheet.format.pattern.SpreadsheetPattern;
import walkingkooka.spreadsheet.importer.SpreadsheetImporterProviders;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadata;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataPropertyName;
import walkingkooka.spreadsheet.meta.store.SpreadsheetMetadataStore;
import walkingkooka.spreadsheet.meta.store.SpreadsheetMetadataStores;
import walkingkooka.spreadsheet.parser.SpreadsheetParserProvider;
import walkingkooka.spreadsheet.parser.SpreadsheetParserProviders;
import walkingkooka.spreadsheet.provider.SpreadsheetProvider;
import walkingkooka.spreadsheet.provider.SpreadsheetProviders;
import walkingkooka.spreadsheet.reference.SpreadsheetSelection;
import walkingkooka.spreadsheet.reference.SpreadsheetViewport;
import walkingkooka.spreadsheet.security.store.SpreadsheetGroupStores;
import walkingkooka.spreadsheet.security.store.SpreadsheetUserStores;
import walkingkooka.spreadsheet.server.SpreadsheetHttpServer;
import walkingkooka.spreadsheet.store.SpreadsheetCellRangeStores;
import walkingkooka.spreadsheet.store.SpreadsheetCellStores;
import walkingkooka.spreadsheet.store.SpreadsheetColumnStores;
import walkingkooka.spreadsheet.store.SpreadsheetExpressionReferenceStores;
import walkingkooka.spreadsheet.store.SpreadsheetLabelStores;
import walkingkooka.spreadsheet.store.SpreadsheetRowStores;
import walkingkooka.spreadsheet.store.repo.SpreadsheetStoreRepositories;
import walkingkooka.spreadsheet.store.repo.SpreadsheetStoreRepository;
import walkingkooka.text.CaseSensitivity;
import walkingkooka.text.CharSequences;
import walkingkooka.text.Indentation;
import walkingkooka.text.LineEnding;
import walkingkooka.tree.expression.ExpressionNumberKind;
import walkingkooka.tree.expression.function.provider.ExpressionFunctionAliasSet;
import walkingkooka.tree.json.marshall.JsonNodeMarshallContexts;
import walkingkooka.tree.json.marshall.JsonNodeMarshallUnmarshallContexts;
import walkingkooka.tree.json.marshall.JsonNodeUnmarshallContexts;
import walkingkooka.util.SystemProperty;

import java.io.IOException;
import java.math.MathContext;
import java.math.RoundingMode;
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
import java.util.function.Supplier;

/**
 * Creates a {@link SpreadsheetHttpServer} with memory stores using a Jetty server using the scheme/host/port from cmd line arguments.
 */
public final class JettyHttpServerSpreadsheetHttpServer implements PublicStaticHelper {

    /**
     * Starts a server on the scheme/host/port passed as arguments, serving files from the current directory.
     */
    public static void main(final String[] args) throws Exception {
        switch (args.length) {
            case 0:
                throw new IllegalArgumentException("Missing serverUrl, defaultLocale, file server root for jetty HttpServer");
            case 1:
                throw new IllegalArgumentException("Missing default Locale, file server root for jetty HttpServer");
            case 2:
                throw new IllegalArgumentException("Missing file server root for jetty HttpServer");
            default:
                startJettyHttpServer(
                        serverUrl(args[0]),
                        locale(args[1]),
                        fileServer(args[2])
                );
                break;
        }
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

    private static WebFile webFile(final Path file) {
        return WebFiles.file(
                file,
                ApacheTikas.fileContentTypeDetector(),
                (b) -> Optional.empty()
        );
    }

    private static void startJettyHttpServer(final AbsoluteUrl serverUrl,
                                             final Locale defaultLocale,
                                             final Function<UrlPath, Either<WebFile, HttpStatus>> fileServer) {
        final Function<SpreadsheetId, SpreadsheetParserProvider> spreadsheetIdToSpreadsheetParserProvider = spreadsheetIdToSpreadsheetParserProvider();

        final Function<SpreadsheetId, SpreadsheetStoreRepository> spreadsheetIdToStoreRepository = spreadsheetIdToStoreRepository(
                Maps.concurrent(),
                storeRepositorySupplier(),
                spreadsheetIdToSpreadsheetParserProvider,
                spreadsheetIdToSpreadsheetMetadata()
        );

        final Supplier<LocalDateTime> now = LocalDateTime::now;

        metadataStore = SpreadsheetMetadataStores.spreadsheetCellStoreAction(
                SpreadsheetMetadataStores.treeMap(
                        prepareMetadataCreateTemplate(
                                defaultLocale
                        ),
                        now
                ),
                (id) -> spreadsheetIdToStoreRepository.apply(id).cells()
        );

        final SpreadsheetHttpServer server = SpreadsheetHttpServer.with(
                serverUrl,
                Indentation.with("  "),
                LineEnding.SYSTEM,
                now,
                systemSpreadsheetProvider(),
                metadataStore,
                JsonNodeMarshallUnmarshallContexts.basic(
                        JsonNodeMarshallContexts.basic(),
                        JsonNodeUnmarshallContexts.basic(
                                ExpressionNumberKind.DEFAULT,
                                MathContext.DECIMAL32
                        )
                ),
                spreadsheetIdToSpreadsheetProvider(),
                spreadsheetIdToStoreRepository,
                fileServer,
                jettyHttpServer(
                        serverUrl.host(),
                        serverUrl.port()
                                .orElse(
                                        serverUrl.scheme().equals(UrlScheme.HTTP) ?
                                                IpPort.HTTP :
                                                IpPort.HTTPS
                                )
                )
        );
        server.start();
    }

    private static SpreadsheetProvider systemSpreadsheetProvider() {
        final SpreadsheetFormatterProvider spreadsheetFormatterProvider = SpreadsheetFormatterProviders.spreadsheetFormatPattern();
        final SpreadsheetParserProvider spreadsheetParserProvider = SpreadsheetParserProviders.spreadsheetParsePattern(
                spreadsheetFormatterProvider
        );

        return SpreadsheetProviders.basic(
                SpreadsheetConvertersConverterProviders.spreadsheetConverters(
                        SpreadsheetMetadata.EMPTY.set(
                                SpreadsheetMetadataPropertyName.LOCALE,
                                Locale.forLanguageTag("EN-AU")
                        ),
                        spreadsheetFormatterProvider,
                        spreadsheetParserProvider
                ), // converterProvider
                SpreadsheetExpressionFunctionProviders.expressionFunctionProvider(CaseSensitivity.INSENSITIVE),
                SpreadsheetComparatorProviders.spreadsheetComparators(),
                SpreadsheetExporterProviders.spreadsheetExport(),
                spreadsheetFormatterProvider,
                SpreadsheetImporterProviders.spreadsheetImport(),
                spreadsheetParserProvider
        );
    }

    /**
     * Shared global singleton {@link SpreadsheetMetadataStore}.
     */
    private static SpreadsheetMetadataStore metadataStore;

    /**
     * The default name given to all empty spreadsheets
     */
    private final static SpreadsheetName DEFAULT_NAME = SpreadsheetName.with("Untitled");

    /**
     * Prepares and merges the default and user locale, loading defaults and more.
     */
    static SpreadsheetMetadata prepareMetadataCreateTemplate(final Locale defaultLocale) {
        final ExpressionFunctionAliasSet functionAliases = SpreadsheetExpressionFunctionProviders.expressionFunctionProvider(
                        SpreadsheetExpressionFunctionNames.CASE_SENSITIVITY
                ).expressionFunctionInfos()
                        .aliasSet();

        return SpreadsheetMetadata.EMPTY
                .set(SpreadsheetMetadataPropertyName.SPREADSHEET_NAME, DEFAULT_NAME)
                .set(SpreadsheetMetadataPropertyName.LOCALE, defaultLocale)
                .set(SpreadsheetMetadataPropertyName.VIEWPORT, INITIAL_VIEWPORT)
                .setDefaults(
                        SpreadsheetMetadata.NON_LOCALE_DEFAULTS
                                .set(SpreadsheetMetadataPropertyName.LOCALE, defaultLocale)
                                .loadFromLocale()
                                .set(SpreadsheetMetadataPropertyName.CELL_CHARACTER_WIDTH, 1)
                                .set(SpreadsheetMetadataPropertyName.DATETIME_OFFSET, Converters.EXCEL_1900_DATE_SYSTEM_OFFSET)
                                .set(SpreadsheetMetadataPropertyName.DEFAULT_YEAR, 1900)
                                .set(SpreadsheetMetadataPropertyName.EXPRESSION_NUMBER_KIND, ExpressionNumberKind.DOUBLE)
                                .set(
                                        SpreadsheetMetadataPropertyName.FIND_FUNCTIONS,
                                        functionAliases
                                ).set(
                                        SpreadsheetMetadataPropertyName.FORMULA_FUNCTIONS,
                                        functionAliases
                                ).set(SpreadsheetMetadataPropertyName.FUNCTIONS, functionAliases
                                ).set(SpreadsheetMetadataPropertyName.PRECISION, MathContext.DECIMAL32.getPrecision())
                                .set(SpreadsheetMetadataPropertyName.ROUNDING_MODE, RoundingMode.HALF_UP)
                                .set(SpreadsheetMetadataPropertyName.TEXT_FORMATTER, SpreadsheetPattern.DEFAULT_TEXT_FORMAT_PATTERN.spreadsheetFormatterSelector())
                                .set(SpreadsheetMetadataPropertyName.TWO_DIGIT_YEAR, 20)
                );
    }

    private final static SpreadsheetViewport INITIAL_VIEWPORT = SpreadsheetViewport.with(
            SpreadsheetSelection.A1.viewportRectangle(
                    1,
                    1
            )
    );

    private static Function<SpreadsheetId, SpreadsheetProvider> spreadsheetIdToSpreadsheetProvider() {
        return (id) -> {
            final SpreadsheetMetadata metadata = metadataStore.loadOrFail(id);

            final SpreadsheetFormatterProvider spreadsheetFormatterProvider = spreadsheetIdToSpreadsheetFormatterProvider()
                    .apply(id);
            final SpreadsheetParserProvider spreadsheetParserProvider = spreadsheetIdToSpreadsheetParserProvider()
                    .apply(id);

            return metadata.spreadsheetProvider(
                    SpreadsheetProviders.basic(
                            SpreadsheetConvertersConverterProviders.spreadsheetConverters(
                                    metadata,
                                    spreadsheetFormatterProvider,
                                    spreadsheetParserProvider
                            ),
                            SpreadsheetExpressionFunctionProviders.expressionFunctionProvider(CaseSensitivity.INSENSITIVE),
                            SpreadsheetComparatorProviders.spreadsheetComparators(),
                            SpreadsheetExporterProviders.spreadsheetExport(),
                            spreadsheetFormatterProvider,
                            SpreadsheetImporterProviders.spreadsheetImport(),
                            spreadsheetParserProvider
                    )
            );
        };
    }

    private static Function<SpreadsheetId, SpreadsheetFormatterProvider> spreadsheetIdToSpreadsheetFormatterProvider() {
        return (id) -> SpreadsheetFormatterProviders.spreadsheetFormatPattern();
    }

    private static Function<SpreadsheetId, SpreadsheetParserProvider> spreadsheetIdToSpreadsheetParserProvider() {
        return (id) -> SpreadsheetParserProviders.spreadsheetParsePattern(
                spreadsheetIdToSpreadsheetFormatterProvider()
                        .apply(id)
        );
    }

    private static Function<SpreadsheetId, SpreadsheetMetadata> spreadsheetIdToSpreadsheetMetadata() {
        return (id) -> metadataStore.loadOrFail(id);
    }
    
    /**
     * Retrieves from the cache or lazily creates a {@link SpreadsheetStoreRepository} for the given {@link SpreadsheetId}.
     */
    private static Function<SpreadsheetId, SpreadsheetStoreRepository> spreadsheetIdToStoreRepository(final Map<SpreadsheetId, SpreadsheetStoreRepository> idToRepository,
                                                                                                      final Supplier<SpreadsheetStoreRepository> repositoryFactory,
                                                                                                      final Function<SpreadsheetId, SpreadsheetParserProvider> spreadsheetIdToSpreadsheetParserProvider,
                                                                                                      final Function<SpreadsheetId, SpreadsheetMetadata> spreadsheetIdToSpreadsheetMetadata) {
        return (id) -> {
            SpreadsheetStoreRepository repository = idToRepository.get(id);
            if (null == repository) {
                final EnvironmentContext environmentContext = spreadsheetIdToSpreadsheetMetadata.apply(id)
                        .environmentContext();
                repository = SpreadsheetStoreRepositories.spreadsheetMetadataAwareSpreadsheetCellStore(
                        id,
                        repositoryFactory.get(),
                        spreadsheetIdToSpreadsheetParserProvider.apply(id),
                        LocalDateTime::now,
                        ProviderContexts.basic(
                                environmentContext,
                                PluginStores.fake()
                        )
                );
                idToRepository.put(id, repository); // TODO add locks etc.
            }
            return repository;
        };
    }

    /**
     * Creates a new {@link SpreadsheetStoreRepository} on demand
     */
    private static Supplier<SpreadsheetStoreRepository> storeRepositorySupplier() {
        return () -> SpreadsheetStoreRepositories.basic(
                SpreadsheetCellStores.treeMap(),
                SpreadsheetExpressionReferenceStores.treeMap(),
                SpreadsheetColumnStores.treeMap(),
                SpreadsheetGroupStores.treeMap(),
                SpreadsheetLabelStores.treeMap(),
                SpreadsheetExpressionReferenceStores.treeMap(),
                metadataStore,
                SpreadsheetCellRangeStores.treeMap(),
                SpreadsheetCellRangeStores.treeMap(),
                SpreadsheetRowStores.treeMap(),
                SpreadsheetUserStores.treeMap()
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

    private JettyHttpServerSpreadsheetHttpServer() {
        throw new UnsupportedOperationException();
    }
}
