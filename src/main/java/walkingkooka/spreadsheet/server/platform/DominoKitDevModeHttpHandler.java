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

import walkingkooka.Binary;
import walkingkooka.net.header.apache.tika.ApacheTikaMediaTypeDetectors;
import walkingkooka.net.http.HttpEntity;
import walkingkooka.net.http.HttpStatusCode;
import walkingkooka.net.http.server.HttpHandler;
import walkingkooka.net.http.server.HttpRequest;
import walkingkooka.net.http.server.HttpResponse;
import walkingkooka.spreadsheet.server.SpreadsheetServerContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

final class DominoKitDevModeHttpHandler implements HttpHandler<SpreadsheetServerContext> {

    /**
     * Singleton
     */
    final static DominoKitDevModeHttpHandler INSTANCE = new DominoKitDevModeHttpHandler();

    private DominoKitDevModeHttpHandler() {
        super();
    }

    @Override
    public void handle(final HttpRequest request,
                       final HttpResponse response,
                       final SpreadsheetServerContext context) {
        final String path = request.url()
            .path()
            .normalize()
            .value();
        try {
            InputStream inputStream;

            final File file = Paths.get("../walkingkooka-spreadsheet-dominokit/target/gwt/out/walkingkooka.spreadsheet.dominokit.App/" + path)
                .toFile()
                .getAbsoluteFile();

            if (file.exists()) {
                inputStream = new FileInputStream(file);
            } else {
                // necessary to try other resources such as /src/main/resources/api-doc
                inputStream = this.getClass()
                    .getResourceAsStream(path);
            }

            if (null != inputStream) {
                final Binary content = Binary.with(
                    inputStream.readAllBytes()
                );
                response.setVersion(request.protocolVersion());
                response.setStatus(HttpStatusCode.OK.status());
                response.setEntity(
                    HttpEntity.EMPTY.setBody(content)
                        .setContentType(
                            ApacheTikaMediaTypeDetectors.apacheTika()
                                .detect(
                                    path,
                                    content
                                )
                        ).setContentLength()
                );
            } else {
                response.setVersion(request.protocolVersion());
                response.setStatus(HttpStatusCode.NOT_FOUND.status());
                response.clearEntity();
            }

        } catch (final IOException cause) {
            response.setVersion(request.protocolVersion());
            response.setStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.status());
            response.setEntity(
                HttpEntity.dumpStackTrace(cause)
            );
        }
    }
}
