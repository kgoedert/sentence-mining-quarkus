package com.github.kgoedert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import com.voicerss.tts.AudioCodec;
import com.voicerss.tts.AudioFormat;
import com.voicerss.tts.Languages;
import com.voicerss.tts.VoiceParameters;
import com.voicerss.tts.VoiceProvider;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusMain
public class SetenceMining implements QuarkusApplication {

    private static final String API_KEY = "9fba43abfe0d4456beb50c7aa255aa44";
    private Path sourceFilePath = null;
    private String targetLanguage = null;

    @Inject
    @RestClient
    AnkiService ankiService;

    @Override
    public int run(String... args) throws Exception {
        checkParameters(args);

        List<SentencePair> sentences = this.parseSentencesFile();

        createAnkiCards(sentences);

        return 0;
    }

    private void createAnkiCards(List<SentencePair> sentences) {
        for (SentencePair pair : sentences) {
            System.out.println("Getting audio file for " + pair.targetLanguage);
            Optional<String> audio = this.getBase64OfAudio(pair.targetLanguage);

            if (audio.isPresent()) {
                String audioFilename = this.uploadAudioFileToAnki(audio.get());
                JsonObject card = this.createCard(pair, audioFilename);
                Response res = this.ankiService.post(card.toString());
                if (res.getStatus() == 200) {
                    System.out.println("Card successfully uploaded to anki.");
                } else {
                    System.err.println("Unable to upload the card to anki");
                    System.exit(1);
                }
            } else {
                System.err.println("It wasn't possible to retrieve the audio file for: " + pair.targetLanguage);
                System.exit(1);
            }
        }
    }

    private JsonObject createCard(SentencePair pair, String audioFilename) {
        String deckName = null;
        switch (targetLanguage) {
            case "de-de":
                deckName = "Deutsch Mining";
                break;
            case "fr-fr":
                deckName = "French Mining";
                break;
            case "es-es":
                deckName = "Español Mining";
                break;
            default:
                break;
        }

        JsonObject fields = new JsonObject()
                .put("Front (Example with word blanked out or missing)",
                        pair.sourceLanguage + "[sound:" + audioFilename + "]")
                .put("Back (a single word/phrase, no context)", pair.sourceLanguage)
                .put("- The full sentence (no words blanked out)", pair.targetLanguage)
                .put("• Make 2 cards? (y = yes, blank = no)", "y");

        JsonObject note = new JsonObject();
        note.put("deckName", deckName).put("modelName", "3. All-Purpose Card").put("fields", fields).put("tags",
                new JsonArray());

        JsonObject params = new JsonObject();
        params.put("note", note);

        return new JsonObject().put("action", "addNote").put("version", 6).put("params", params);
    }

    private String uploadAudioFileToAnki(String audio) {
        String filename = "_" + System.currentTimeMillis() + ".mp3";
        JsonObject params = new JsonObject().put("filename", filename).put("data", audio);

        JsonObject action = new JsonObject().put("action", "storeMediaFile").put("version", 6).put("params", params);

        Response res = this.ankiService.post(action.toString());

        if (res.getStatus() == 200) {
            return filename;
        } else {
            System.err.println(res.getEntity().toString());
            return null;
        }
    }

    private Optional<String> getBase64OfAudio(String phrase) {
        VoiceProvider tts = new VoiceProvider(API_KEY);

        VoiceParameters params = new VoiceParameters(phrase, this.targetLanguage);
        params.setCodec(AudioCodec.MP3);
        params.setFormat(AudioFormat.Format_44KHZ.AF_44khz_16bit_stereo);
        params.setBase64(true);
        params.setSSML(false);
        params.setRate(0);

        Optional<String> result = null;

        try {
            String base64 = tts.speech(params);
            result = Optional.of(base64);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private List<SentencePair> parseSentencesFile() {
        List<SentencePair> sentences = new ArrayList<>();
        try {
            List<String> allLines = Files.readAllLines(this.sourceFilePath);

            int counter = 0;
            SentencePair pair = null;
            for (String line : allLines) {
                if (counter == 0) {
                    pair = new SentencePair();
                    pair.targetLanguage = line;
                    counter++;
                    continue;
                }

                if (counter == 1) {
                    pair.sourceLanguage = line;
                    counter++;
                    continue;
                }

                if (counter == 2) {
                    counter = 0;
                    sentences.add(pair);
                    continue;
                }
            }
            sentences.add(pair);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return sentences;
    }

    private void checkParameters(String... args) {
        if (args[0].equals("-h")) {
            System.err.println("Parameter 0: The source file.");
            System.err.println("Parameter 1: The target language. Possible values are: de, fr, es");
            System.exit(0);
        }

        if (args[0] == null) {
            System.err.println("You need to inform the source file full path.");
            System.exit(1);
        }

        if (args[1] == null) {
            System.err.println("You need to inform the name of the anki deck");
            System.exit(1);
        }

        if (args[1].equals("de") || args[1].equals("fr") || args[1].equals("es")) {
            this.sourceFilePath = Paths.get(args[0]);

            switch (args[1]) {
                case "de":
                    this.targetLanguage = Languages.German_Germany;
                    break;
                case "fr":
                    this.targetLanguage = Languages.French_France;
                    break;
                case "es":
                    this.targetLanguage = Languages.Spanish_Spain;
                    break;
                default:
                    break;
            }
        } else {
            System.err.println("This language is not supported.");
            System.exit(1);
        }

    }

}