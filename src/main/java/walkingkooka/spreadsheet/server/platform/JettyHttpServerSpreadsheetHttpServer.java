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
import walkingkooka.math.Fraction;
import walkingkooka.net.HostAddress;
import walkingkooka.net.IpPort;
import walkingkooka.net.UrlPath;
import walkingkooka.net.UrlScheme;
import walkingkooka.net.email.EmailAddress;
import walkingkooka.net.header.apache.tika.ApacheTikas;
import walkingkooka.net.http.HttpStatus;
import walkingkooka.net.http.HttpStatusCode;
import walkingkooka.net.http.server.HttpRequest;
import walkingkooka.net.http.server.HttpResponse;
import walkingkooka.net.http.server.HttpServer;
import walkingkooka.net.http.server.WebFile;
import walkingkooka.net.http.server.WebFiles;
import walkingkooka.net.http.server.jetty.JettyHttpServer;
import walkingkooka.reflect.PublicStaticHelper;
import walkingkooka.spreadsheet.SpreadsheetId;
import walkingkooka.spreadsheet.SpreadsheetName;
import walkingkooka.spreadsheet.compare.SpreadsheetComparatorProvider;
import walkingkooka.spreadsheet.compare.SpreadsheetComparatorProviders;
import walkingkooka.spreadsheet.format.pattern.SpreadsheetPattern;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadata;
import walkingkooka.spreadsheet.meta.SpreadsheetMetadataPropertyName;
import walkingkooka.spreadsheet.meta.store.SpreadsheetMetadataStore;
import walkingkooka.spreadsheet.meta.store.SpreadsheetMetadataStores;
import walkingkooka.spreadsheet.reference.SpreadsheetSelection;
import walkingkooka.spreadsheet.reference.SpreadsheetViewport;
import walkingkooka.spreadsheet.security.store.SpreadsheetGroupStores;
import walkingkooka.spreadsheet.security.store.SpreadsheetUserStores;
import walkingkooka.spreadsheet.server.SpreadsheetHttpServer;
import walkingkooka.spreadsheet.server.context.SpreadsheetContexts;
import walkingkooka.spreadsheet.server.expression.function.SpreadsheetServerExpressionFunctions;
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
import walkingkooka.tree.expression.function.provider.ExpressionFunctionProvider;
import walkingkooka.util.SystemProperty;

import java.io.IOException;
import java.math.BigDecimal;
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
import java.util.function.BiConsumer;
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
                throw new IllegalArgumentException("Missing scheme, host, port, defaultLocale, file server root for jetty HttpServer");
            case 1:
                throw new IllegalArgumentException("Missing host, port, defaultLocale, file server root for jetty HttpServer");
            case 2:
                throw new IllegalArgumentException("Missing port, defaultLocale, file server root for jetty HttpServer");
            case 3:
                throw new IllegalArgumentException("Missing default Locale, file server root for jetty HttpServer");
            case 4:
                throw new IllegalArgumentException("Missing file server root for jetty HttpServer");
            default:
                startJettyHttpServer(
                        urlScheme(args[0]),
                        hostAddress(args[1]),
                        port(args[2]),
                        locale(args[3]),
                        fileServer(args[4])
                );
                break;
        }
    }

    private static UrlScheme urlScheme(final String string) {
        final UrlScheme scheme;
        try {
            scheme = UrlScheme.with(string);
        } catch (final IllegalArgumentException cause) {
            System.err.println("Invalid scheme: " + cause.getMessage());
            throw cause;
        }
        return scheme;
    }

    private static HostAddress hostAddress(final String string) {
        final HostAddress host;
        try {
            host = HostAddress.with(string);
        } catch (final IllegalArgumentException cause) {
            System.err.println("Invalid hostname: " + cause.getMessage());
            throw cause;
        }
        return host;
    }

    private static IpPort port(final String string) {
        final IpPort port;
        try {
            port = IpPort.with(Integer.parseInt(string));
        } catch (final RuntimeException cause) {
            System.err.println("Invalid port: " + cause.getMessage());
            throw cause;
        }
        return port;
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
            throw new IllegalArgumentException("Base directory is not a directory " + CharSequences.quoteAndEscape(basePath));
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

    private static void startJettyHttpServer(final UrlScheme scheme,
                                             final HostAddress host,
                                             final IpPort port,
                                             final Locale defaultLocale,
                                             final Function<UrlPath, Either<WebFile, HttpStatus>> fileServer) {

        final Function<SpreadsheetId, SpreadsheetStoreRepository> spreadsheetIdToStoreRepository = spreadsheetIdToStoreRepository(
                Maps.concurrent(),
                storeRepositorySupplier()
        );

        metadataStore = SpreadsheetMetadataStores.spreadsheetCellStoreAction(
                SpreadsheetMetadataStores.treeMap(),
                (id) -> spreadsheetIdToStoreRepository.apply(id).cells()
        );

        final SpreadsheetHttpServer server = SpreadsheetHttpServer.with(
                scheme,
                host,
                port,
                Indentation.with("  "),
                LineEnding.SYSTEM,
                LocalDateTime::now,
                createMetadata(defaultLocale, metadataStore),
                metadataStore,
                JettyHttpServerSpreadsheetHttpServer::spreadsheetMetadataStamper,
                fractioner(),
                spreadsheetIdSpreadsheetComparatorProvider(),
                spreadsheetIdToExpressionFunctionProvider(),
                spreadsheetIdToStoreRepository,
                SpreadsheetContexts::jsonHateosContentType,
                fileServer,
                jettyHttpServer(host, port)
        );
        server.start();
    }

    /**
     * Shared global singleton {@link SpreadsheetMetadataStore}.
     */
    private static SpreadsheetMetadataStore metadataStore;

    /**
     * Creates a function which merges the given {@link Locale} and then saves it to the {@link SpreadsheetMetadataStore}.
     */
    private static Function<Optional<Locale>, SpreadsheetMetadata> createMetadata(final Locale defaultLocale,
                                                                                  final SpreadsheetMetadataStore store) {
        // TODO https://github.com/mP1/walkingkooka-spreadsheet-server/issues/134
        // JettyHttpServerSpreadsheetHttpServer: retrieve user from context when creating initial SpreadsheetMetadata
        final EmailAddress user = EmailAddress.parse("user123@example.com");

        // if a Locale is given load a Metadata with those defaults.
        return (userLocale) -> {
            final LocalDateTime now = LocalDateTime.now();

            return store.save(
                    prepareInitialMetadata(
                            user,
                            now,
                            userLocale,
                            defaultLocale
                    )
            );
        };
    }

    /**
     * The default name given to all empty spreadsheets
     */
    private final static SpreadsheetName DEFAULT_NAME = SpreadsheetName.with("Untitled");

    /**
     * Prepares and merges the default and user locale, loading defaults and more.
     */
    static SpreadsheetMetadata prepareInitialMetadata(final EmailAddress user,
                                                      final LocalDateTime now,
                                                      final Optional<Locale> userLocale,
                                                      final Locale defaultLocale) {
        final Locale localeOrDefault = userLocale.orElse(defaultLocale);
        return SpreadsheetMetadata.EMPTY
                .set(SpreadsheetMetadataPropertyName.CREATOR, user)
                .set(SpreadsheetMetadataPropertyName.CREATE_DATE_TIME, now)
                .set(SpreadsheetMetadataPropertyName.MODIFIED_BY, user)
                .set(SpreadsheetMetadataPropertyName.MODIFIED_DATE_TIME, now)
                .set(SpreadsheetMetadataPropertyName.SPREADSHEET_NAME, DEFAULT_NAME)
                .set(SpreadsheetMetadataPropertyName.LOCALE, localeOrDefault)
                .set(SpreadsheetMetadataPropertyName.VIEWPORT, INITIAL_VIEWPORT)
                .setDefaults(
                        SpreadsheetMetadata.NON_LOCALE_DEFAULTS
                                .set(SpreadsheetMetadataPropertyName.LOCALE, localeOrDefault)
                                .loadFromLocale()
                                .set(SpreadsheetMetadataPropertyName.CELL_CHARACTER_WIDTH, 1)
                                .set(SpreadsheetMetadataPropertyName.DATETIME_OFFSET, Converters.EXCEL_1900_DATE_SYSTEM_OFFSET)
                                .set(SpreadsheetMetadataPropertyName.DEFAULT_YEAR, 1900)
                                .set(SpreadsheetMetadataPropertyName.EXPRESSION_NUMBER_KIND, ExpressionNumberKind.DOUBLE)
                                .set(SpreadsheetMetadataPropertyName.PRECISION, MathContext.DECIMAL32.getPrecision())
                                .set(SpreadsheetMetadataPropertyName.ROUNDING_MODE, RoundingMode.HALF_UP)
                                .set(SpreadsheetMetadataPropertyName.TEXT_FORMAT_PATTERN, SpreadsheetPattern.DEFAULT_TEXT_FORMAT_PATTERN)
                                .set(SpreadsheetMetadataPropertyName.TWO_DIGIT_YEAR, 20)
                );
    }

    private final static SpreadsheetViewport INITIAL_VIEWPORT = SpreadsheetViewport.with(
            SpreadsheetSelection.A1.viewportRectangle(
                    1,
                    1
            )
    );

    private static SpreadsheetMetadata spreadsheetMetadataStamper(final SpreadsheetMetadata metadata) {
        return metadata.set(
                SpreadsheetMetadataPropertyName.MODIFIED_DATE_TIME,
                LocalDateTime.now()
        );
    }

    private static Function<BigDecimal, Fraction> fractioner() {
        return (n) -> {
            throw new UnsupportedOperationException();
        };
    }

    private static Function<SpreadsheetId, SpreadsheetComparatorProvider> spreadsheetIdSpreadsheetComparatorProvider() {
        return (id) -> SpreadsheetComparatorProviders.builtIn();
    }

    private static Function<SpreadsheetId, ExpressionFunctionProvider> spreadsheetIdToExpressionFunctionProvider() {
        return (id) -> SpreadsheetServerExpressionFunctions.expressionFunctionProvider(CaseSensitivity.INSENSITIVE);
    }

    /**
     * Retrieves from the cache or lazily creates a {@link SpreadsheetStoreRepository} for the given {@link SpreadsheetId}.
     */
    private static Function<SpreadsheetId, SpreadsheetStoreRepository> spreadsheetIdToStoreRepository(final Map<SpreadsheetId, SpreadsheetStoreRepository> idToRepository,
                                                                                                      final Supplier<SpreadsheetStoreRepository> repositoryFactory) {
        return (id) -> {
            SpreadsheetStoreRepository repository = idToRepository.get(id);
            if (null == repository) {
                repository = SpreadsheetStoreRepositories.spreadsheetMetadataAwareSpreadsheetCellStore(
                        id,
                        repositoryFactory.get(),
                        LocalDateTime::now
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
    private static Function<BiConsumer<HttpRequest, HttpResponse>, HttpServer> jettyHttpServer(final HostAddress host,
                                                                                               final IpPort port) {
        return (handler) -> JettyHttpServer.with(host, port, handler);
    }

    private JettyHttpServerSpreadsheetHttpServer() {
        throw new UnsupportedOperationException();
    }
}
