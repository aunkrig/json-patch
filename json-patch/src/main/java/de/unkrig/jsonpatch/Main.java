
/*
 * json-patch - A command-line tool for modifying JSON documents
 *
 * Copyright (c) 2023, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.jsonpatch;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.filetransformation.FileTransformations;
import de.unkrig.commons.file.filetransformation.FileTransformer.Mode;
import de.unkrig.commons.util.CommandLineOptionException;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.CommandLineOptionGroup;
import de.unkrig.commons.util.annotation.CommandLineOption.Cardinality;
import de.unkrig.jsonpatch.JsonPatch.RemoveMode;
import de.unkrig.jsonpatch.JsonPatch.SetMode;

public
class Main {

	// ========================================== CONFIGURATION ==========================================
	private Charset         inCharset  = StandardCharsets.UTF_8;
	private Charset         outCharset = StandardCharsets.UTF_8;
    private boolean         keepOriginals;
    private final JsonPatch jsonPatch = new JsonPatch();
    // ========================================== END CONFIGURATION ==========================================

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public static void
    help() throws IOException {

        CommandLineOptions.printResource(Main.class, "main(String[]).txt", Charset.forName("UTF-8"), System.out);

        System.exit(0);
    }

    /**
     * For in-place file transformations, keep copies of the originals.
     * @main.commandLineOptionGroup Input-Processing
     */
    @CommandLineOption public void
    keep() { this.keepOriginals = true; }

    /**
     * Input encoding charset (default UTF-8)
     * @main.commandLineOptionGroup Input-Processing
     */
    @CommandLineOption public void
    setInCharset(Charset inCharset) { this.inCharset = inCharset; }

    /**
     * Output encoding charset (default UTF-8)
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setOutCharset(Charset outCharset) { this.outCharset = outCharset; }

    /**
     * Allow JSON data which does not strictly comply with the JSON specification.
     * @main.commandLineOptionGroup Input-Processing
     */
    @CommandLineOption public void
    lenient() { this.jsonPatch.getGsonBuilder().setLenient(); }

    /**
     * Wrap and indent json objects and arrays.
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    prettyPrinting() { this.jsonPatch.getGsonBuilder().setPrettyPrinting(); }

    /**
     * Do not ampersand-escape HTML characters such as {@code <} and {@code >}.
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    disableHtmlEscaping() { this.jsonPatch.getGsonBuilder().disableHtmlEscaping(); }

    /** Suboptions for {@link Main#addSet(SetOptions, String, String)}. */
    public static
    class SetOptions {

        public SetMode mode = SetMode.ANY;

        @CommandLineOption(group = ExistingXorNonExisting.class) public void existing()    { this.mode = SetMode.EXISTING; }
        @CommandLineOption(group = ExistingXorNonExisting.class) public void nonExisting() { this.mode = SetMode.NON_EXISTING; }
    }
    @CommandLineOptionGroup public interface ExistingXorNonExisting {}

    /**
     * Add or change one array element or object member.
     * <dl>
     *   <dt>--existing</dt>
     *   <dd>Verify that the object member resp. array element already exists</dd>
     *   <dt>--non-existing</dt>
     *   <dd>Verify that the object member resp. array element does not exist already</dd>
     * </dl>
     * <p>
     *   The <var>spec</var> is formed as follows:
     * </p>
     * <dl>
     *   <dt>(empty string)</dt>
     *   <dd>Replace the entire document.</dd>
     *   <dt><var>path</var>{@code .}<var>object-member-name</var></dt>
     *   <dd>Change or add the given object member.</dd>
     *   <dt><var>path</var>{@code []}</dt>
     *   <dt><var>path</var>{@code [}<var>arraysize</var>{@code ]}</dt>
     *   <dd>Append to the array.</dd>
     *   <dt><var>path</var>{@code [}<var>0...arraysize-1</var>{@code ]}</dt>
     *   <dd>Change the array element with the given index.</dd>
     *   <dt><var>path</var>{@code [}<var>-arraysize...-1</var>{@code ]}</dt>
     *   <dd>Change the array element with the given index plus <var>arraysize</var>.</dd>
     * </dl>
     * 
     * @param setOptions            [ --existing | --non-existing ]
     * @param jsonDocumentOrFile    ( <var>json-document</var> | {@code @}<var>file-name</var> | "-" )
     * @main.commandLineOptionGroup Document-Transformation
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addSet(SetOptions setOptions, String spec, String jsonDocumentOrFile) throws IOException {
        this.jsonPatch.addSet(spec, JsonPatch.jsonDocumentOrFile(jsonDocumentOrFile, this.inCharset), setOptions.mode);
    }

    /**Suboptions for {@link Main#addRemove(RemoveOptions, String)}. */
    public static
    class RemoveOptions {

        public RemoveMode mode = RemoveMode.ANY;

        @CommandLineOption public void existing() { this.mode = RemoveMode.EXISTING; }
    }

    /**
     * Remove one object member or array element.
     * <dl>
     *   <dt>--existing</dt>
     *   <dd>Verify that the object member resp. array element already exists</dd>
     * </dl>
     * <p>
     *   The <var>spec</var> is formed as follows:
     * </p>
     * <dl>
     *   <dt><var>path</var>{@code '.'}<var>object-member-name</var></dt>
     *   <dd>Remove the given object member (if it exists).</dd>
     *   <dt><var>path</var>{@code [}<var>0...arraysize-1</var>{@code ]}</dt>
     *   <dd>Remove the array element with the given index.</dd>
     *   <dt><var>path</var>{@code [}<var>-arraysize...-1</var>{@code ]}</dt>
     *   <dd>Remove the array element with the given index plus <var>arraysize</var>.</dd>
     * </dl>
     * 
     * @param removeOptions         [ --existing ]
     * @main.commandLineOptionGroup Document-Transformation
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addRemove(RemoveOptions removeOptions, String spec) throws IOException {
        this.jsonPatch.addRemove(spec, removeOptions.mode);
    }

    /**
     * Insert an element into an array.
     * <p>
     *   The <var>spec</var> is formed as follows:
     * </p>
     * <dl>
     *   <dt><var>path</var>{@code []}</dt>
     *   <dt><var>path</var>{@code [}<var>arraysize</var>{@code ]}</dt>
     *   <dd>Append to the array.</dd>
     *   <dt><var>path</var>{@code [}<var>0...arraysize-1</var>{@code ]}</dt>
     *   <dd>Insert before the element with the given index.</dd>
     *   <dt><var>path</var>{@code [}<var>-arraysize...-1</var>{@code ]}</dt>
     *   <dd>Insert before the element with the given index plus <var>arraysize</var>.</dd>
     * </dl>
     *
     * @param jsonDocumentOrFile    ( <var>json-document</var> | @<var>file</var> | "-")
     * @main.commandLineOptionGroup Document-Transformation
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addInsert(String spec, String jsonDocumentOrFile) throws IOException {
        this.jsonPatch.addInsert(spec, JsonPatch.jsonDocumentOrFile(jsonDocumentOrFile, this.inCharset));
    }

    /**
     * A command line utility that modifies JSON documents.
     * <h2>Usage</h2>
     * <dl>
     *   <dt>{@code jsonpatch} [ <var>option</var> ... ]</dt>
     *   <dd>
     *     Parse a JSON document from STDIN, modify it, and print it to STDOUT.
     *   </dd>
     *   <dt>{@code jsonpatch} [ <var>option</var> ... ] !<var>json-document</var></dt>
     *   <dd>
     *     Parse the literal <var>JSON-document</var>, modify it, and print it to STDOUT.
     *   </dd>
     *   <dt>{@code jsonpatch} [ <var>option</var> ] <var>file</var></dt>
     *   <dd>
     *     Transform the JSON document in <var>file</var> to STDOUT.
     *   </dd>
     *   <dt>{@code jsonpatch} [ <var>option</var> ] <var>file1</var> <var>file2</var></dt>
     *   <dd>
     *     Read the JSON document in <var>file1</var>, modify it, and write it to (existing or new) <var>file2</var>.
     *   </dd>
     *   <dt>{@code jsonpatch} [ <var>option</var> ] <var>file</var> ... <var>existing-dir</var></dt>
     *   <dd>
     *     Read the JSON document in each <var>file</var>, modify it, and write it to a file in <var>existing-dir</var>.
     *   </dd>
     * </dl>
     *
     * <h2>Options</h2>
     *
     * <h3>General</h3>
     * <dl>
     * {@main.commandLineOptions}
     * </dl>
     *
     * <h3>Input processing</h3>
     * <dl>
     * {@main.commandLineOptions Input-Processing}
     * </dl>
     *
     * <h3>Document transformation</h3>
     * <dl>
     * {@main.commandLineOptions Document-Transformation}
     * </dl>
     *
     * <h3>Output generation</h3>
     * <dl>
     * {@main.commandLineOptions Output-Generation}
     * </dl>
     *
     * <h2>Paths</h2>
     * <p>
     *   Many of the options specify a path from the root of the JSON document to a node, as follows:
     * </p>
     * <dl>
     *   <dt>{@code .}<var>object-member-name</var></dt>
     *   <dd>Use the given object member.</dd>
     *   <dt>{@code [}<var>0...arraySize-1</var>{@code ]}</dt>
     *   <dd>Use the array element with the given index index.</dd>
     *   <dt>{@code [}<var>-arraySize...-1</var>{@code ]}</dt>
     *   <dd>Use the array element with the given index plus <var>arraySize</var>.</dd>
     * </dl>
     */
    public static void
    main(String[] args) throws IOException, CommandLineOptionException {

        // Configure a "Main" object from the command line options.
        Main main = new Main();
        args = CommandLineOptions.parse(args, main);

        if (args.length == 1 && args[0].startsWith("!")) {

            // Parse single command line argument as a JSON document, and transform it to STDOUT.
            main.jsonPatch.transform(new StringReader(args[0].substring(1)), System.out, main.outCharset);
        } else {
            FileTransformations.transform(
                args,                                                                                // args
                true,                                                                                // unixMode
                main.jsonPatch.fileTransformer(main.inCharset, main.outCharset, main.keepOriginals), // fileTransformer
                main.jsonPatch.contentsTransformer(main.inCharset, main.outCharset),                 // contentsTransformer
                Mode.TRANSFORM,                                                                      // mode
                ExceptionHandler.defaultHandler()                                                    // exceptionHandler
            );
        }
    }
}
