
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

package de.unkrig.zz.jsonpatch;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.filetransformation.FileContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileTransformations;
import de.unkrig.commons.file.filetransformation.FileTransformer.Mode;
import de.unkrig.commons.util.CommandLineOptionException;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.CommandLineOption.Cardinality;
import de.unkrig.zz.jsonpatch.JsonPatch.RemoveMode;
import de.unkrig.zz.jsonpatch.JsonPatch.SetMode;

public
class Main {

    private final JsonPatch jsonPatch = new JsonPatch();
    private boolean keepOriginals;

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public static void
    help() throws IOException {

        CommandLineOptions.printResource(Main.class, "main(String[]).txt", Charset.forName("UTF-8"), System.out);

        System.exit(0);
    }

    /**
     * Wrap and indent json objects and arrays.
     */
    @CommandLineOption public void
    prettyPrinting() { this.jsonPatch.setPrettyPrinting(true); }

    /**
     * If existing files would be overwritten, keep copies of the originals.
     */
    @CommandLineOption public void
    keep() { this.keepOriginals = true; }

    /**
     * Adds or changes one array element or object member.
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
     * @param setOptions         [ --existing | --non-existing ]
     * @param jsonDocumentOrFile ( <var>json-document</var> | {@code @}<var>file-name</var> )
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addSet(SetOptions setOptions, String spec, String jsonDocumentOrFile) throws IOException {
        this.jsonPatch.addSet(spec, JsonPatch.jsonDocumentOrFile(jsonDocumentOrFile), setOptions.mode);
    }

    /**
     * Removes one array element or object member.
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
     * @param removeOptions [ --existing ]
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addRemove(RemoveOptions removeOptions, String spec) throws IOException {
        this.jsonPatch.addRemove(spec, removeOptions.mode);
    }

    /**
     * Inserts an element into an array.
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
     * @param jsonDocumentOrFile ( <var>json-document</var> | @<var>file</var> )
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addInsert(String spec, String jsonDocumentOrFile) throws IOException {
        this.jsonPatch.addInsert(spec, JsonPatch.jsonDocumentOrFile(jsonDocumentOrFile));
    }

    public static
    class SetOptions {

        public SetMode mode = SetMode.ANY;

        /**
         * The object member or array element affected by the operation must exist (and is replaced).
         */
        @CommandLineOption public void setExisting()    { this.mode = SetMode.EXISTING; }
        
        /**
         * The object member or array element affected by the operation must not exist (and is created).
         */
        @CommandLineOption public void setNonExisting() { this.mode = SetMode.NON_EXISTING; }
    }


    public static
    class RemoveOptions {

        public RemoveMode mode = RemoveMode.ANY;

        /**
         * The specified object member must exist.
         */
        @CommandLineOption public void setExisting() { this.mode = RemoveMode.EXISTING; }
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
     *     Transforms <var>file</var> in-place.
     *   </dd>
     *   <dt>{@code jsonpatch} [ <var>option</var> ] <var>file1</var> <var>file2</var></dt>
     *   <dd>
     *     Read the JSON documents in <var>file1</var>, modify them, and write them to (existing or new) <var>file2</var>.
     *   </dd>
     *   <dt>{@code jsonpatch} [ <var>option</var> ] <var>file</var> ... <var>existing-dir</var></dt>
     *   <dd>
     *     Read the JSON document in each <var>file</var>, modify it, and write it to a file in <var>existing-dir</var>.
     *   </dd>
     * </dl>
     *
     * <h2>Options</h2>
     *
     * <dl>
     * {@main.commandLineOptions}
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
            main.jsonPatch.transform(new StringReader(args[0].substring(1)), System.out);
        } else
        if (args.length >= 1) {

            // Transform a set of files, in-place or out-of-place.
            FileTransformations.transform(
                args,                             // args
                new FileContentsTransformer(      // fileTransformer
                    main.jsonPatch.contentsTransformer(),
                    main.keepOriginals
                ),
                Mode.TRANSFORM,                   // mode
                ExceptionHandler.defaultHandler() // exceptionHandler
            );
        } else
        {

            // Transform JSON document from STDIN to STDOUT.
            main.jsonPatch.contentsTransformer().transform("-", System.in, System.out);
        }
    }
}
