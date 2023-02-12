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

/**
 * Runs {@link JettyHttpServerSpreadsheetHttpServer#main} on port 12345 and points the file server to read various gwt assets from walkingkooka-spreadsheet-dominokit
 */
public final class JettyHttpServerSpreadsheetHttpServerDominoKit {
    public static void main(final String[] args) throws Exception {
        JettyHttpServerSpreadsheetHttpServer.main(
                new String[]{
                        "http",
                        "localhost",
                        "12345",
                        "EN-GB",
                        //"./target/classes" // walkingkooka-spreadsheet-domino will place its files here.
                        fileSystemUris(
                                "file:///Users/miroslav/repos-github/walkingkooka-spreadsheet-server-platform/src/main/resources/", // api-doc etc
                                "file:///Users/miroslav/repos-github/walkingkooka-spreadsheet-dominokit/target/gwt/out/walkingkooka.spreadsheet.dominokit.App/", // gwt output
                                "jar:file:///Users/miroslav/.m2/repository/org/dominokit/domino-ui-webjar/1.0.0-RC17/domino-ui-webjar-1.0.0-RC17.jar!META-INF/resources/webjars/" // domino-ui *.css
                        )
                }
        );
    }

    private static String fileSystemUris(final String...uris) {
        return String.join(
                ",",
                uris
        );
    }
}
