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

import walkingkooka.text.Indentation;
import walkingkooka.text.LineEnding;

import java.util.Currency;

/**
 * Runs {@link JettyHttpServerSpreadsheetHttpServer#main} on port 12345 and points the file server to read various gwt assets from walkingkooka-spreadsheet-dominokit
 */
public final class JettyHttpServerSpreadsheetHttpServerDominoKit {
    public static void main(final String[] args) throws Exception {
        JettyHttpServerSpreadsheetHttpServer.main(
            new String[]{
                "UTF-8", // charset
                "http://localhost:12345", // httpServerUrl
                "2000", // apacheSshdPort
                Currency.getInstance("GBP").toString(),
                Indentation.SPACES2.toString(), // indentation
                LineEnding.NL.name(), // lineEnding
                "en-AU", // defaultLocale
                JettyHttpServerSpreadsheetHttpServer.DEV_MODE,
                "defaultAuthenticatedUser@example.com" // systemUser
            }
        );
    }
}
