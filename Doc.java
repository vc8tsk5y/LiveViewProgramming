Clerk.clear();
Clerk.markdown(
    Text.fillOut(
        """
        # Minecraft Klon

        _Tim Zechmeister_, _Technische Hochschule Mittelhessen_

        _5442812_

        ## how to play
        in der Jshell nach der initialisierung des lvp projekts kann `Game game = new Game()` zum initialisieren des Spiels genutzt werden.
        - mit w, a, s, d, & leertaste kann man sich vortbewegen
        - mit Linksklick wird der Mauscursor gelockt um sich dann umschauen zu können
        - mit Rechts- und Linksklick können blöcke platziert und abgebaut werden
        - mit 1, 2, 3, kann man zwischen zu platzierenden Blöcken wechseln (Stein, Grass, Erde)

        ## Szenario 1 Struktur der Welt
        Mit folgender Methode die bei der Initialisierung von Game aufgerufen wird,
        lade ich die Nötigen java scirpt datein, erstelle einen canvas (der im js code immer an die Browser größe angepasst wird)
        und initialisiere die das webgl objekt.
        ```java
        ${initWebgl}
        ```

        Darauf wie Blöcke gespeichret werden gehe ich in Szenario 4 Welt in chunks teilen weiter ein.
        Grundlegend erstelle ich ein 3D array aus Block typen(Luft, Stein, Grass, Erde)
        ```java
        ${BlockType}
        ```

        ## Szenario 2 Steuerung
        Der Java Script teil sendet beim drücken einer taste ein `KeyDown` und `KeyUp` um gedrückthalten mehrerer Tasten zu ermöglichen.\n
        Maus Bewegungen werden aus Performanz gründen ertmals zusammen addiert und aufeinmal (maximal 60 mal die Sekunde) Gesendet.
        ```js
        ${mnKEventjs}
        ```

        In Java lege ich Vectoren für die Spielerposition und die Blickrichtung sowie Winkel der horizontalen und vertikalen Rotation.
        ```java
        ${playerMovement}
        ```

        In Java wandle ich die ankommenden json daten in brauchbare Srings um.\n
        Für Maus Bewegungen berechne ich den Blickrichtungs Vector aus den zuvorberechneten horizontalen und vertikalen Winkeln.
        ```java
        ${mouseEventjava}
        ```

        Für Links- und Rechtsklicks wird ein linie von der cameraposition des Spielers
        zu dem Nächsten Block in 5 Blöcken entfernung mit dem Amanatides-Woo raycasting Algorithmus berechnet.
        Diese Methode gibt entweder die Position des Getroffenen Blocks zurück,
        um diesen dann abzubauen oder die Position des Blocks der zuletzt von dem algorithmus durchlaufen wurde
        um die anliegenden Blockposition zum plazieren eines neuen Blocks zu erhalten.
        ```java
        ${raycasting}
        ```

        Um in einem festen intervall die spielerposition anzupassen habe ich einen rate limiter eingebaut.
        Alle momentan gedrückten tasten werden in dem activeKeys Set gespeichert.
        Ich gehe in einem späteren Abschnitt darauf ein was passiert wenn die Leertaste in diesem Set enthalten ist.
        Wenn 1, 2, oder 3 enthalten ist wird der ausgewählte Blocktyp geändert.
        Wenn W, A, S, oder/und D im Set enthalten sind wird ein Vektor in die Entsprechende richtung angelegt der normalisiert und mit der Bewegungsgeschwindigkeit skaliert wird.
        ```java
        ${clickEventjava}
        ```

        Der Bewegungsvektor wird dann von der Spielerposition aus auf Kollisionen getestet und umgeformt.
        ```java
        ${collision}
        ```

        ## Szenario 4 Welt in chunks teilen
        Jeder Chunk ist 16x64x16 Blöcke groß. Die Blöcke der Chunks sind zwar permanent für java einzusehen,
        die Blöcke werden aber erst wenn der Spieler in die Nähe kommt an den WebGL java script teil geschickt der sie für den Benutzer sichtbar macht, um Performance zu steigern.
        Falls der chunks noch nicht in Java existiert wird er mit einem zufälligen terrain generiert aber dazu in Szenario 5 zufällige Weltgeneration mehr.
        Die load Methode sendet alle zu ladenden Blöcke an den WebGL teil.
        Die unload als gegenstück zur load sendet alle zu entfernenden Blöcke an den WebGL teil.
        ```java
        ${chunks}
        ```

        Alle Chunks werden in einer Hashmap gespeichert und als key wird ein Hash aus der Position des Chunks berechnet.
        Die handleChunkRendering Methode erstellt und updated Sets von geladenen, zu ladende und zu entladenden Chunks und lässt diese auch immer laden oder entladen.
        (auf den Part mit dem neuladen der Chunkränder gehe ich in Szenario 3 performantes Rendering noch ein).
        ```java
        ${chunkutil}
        ```

        """, Map.of(
            "initWebgl", Text.cutOut("./views/WebGL/Game.java", "// initialize webgl"),
            "BlockType", Text.cutOut("./views/WebGL/BlockType.java", "// BlockType"),
            "mnKEventjs", Text.cutOut("./views/WebGL/handleMnKEvent.js", "// mnKEvent"),
            "playerMovement", Text.cutOut("./views/WebGL/Game.java", "// Player movement"),
            "mouseEventjava", Text.cutOut("./views/WebGL/Game.java", "// mouseEvent"),
            "clickEventjava", Text.cutOut("./views/WebGL/Game.java", "// clickEvent"),
            "raycasting", Text.cutOut("./views/WebGL/Game.java", "// raycasting"),
            "collision", Text.cutOut("./views/WebGL/Game.java", "// collision"),
            "chunks", Text.cutOut("./views/WebGL/Game.java", "// chunks"),
            "chunkutil", Text.cutOut("./views/WebGL/Game.java", "// chunkutil")
        )
    )
);

Clerk.markdown(
    Text.fillOut(
        """
        ## Szenario 3 performantes Rendering
        Aus Performanz gründen werden nur die Blöcke geladen die tatsächlich sichtbar sind.
        Blöcke die nicht geladen werden haben keine Seite, die mit Luft in Kontakt ist das ist anders als im Funktionsversprechen nicht mit einer Voxel grid.
        Die areaReload Methode wird benutzt um neu zu berechnen welche Blöcke in einem Bereich sichtbar sind zum Beispiel wenn Blöcke plaziert oder abgebaut werden.
        Die reloadChunkEdge Methode ist recht spezifisch da Blöcke an rändern von Chunks nur sichtbar sind wenn der anliegende Chunk noch nicht existiert
        diese seite soll neu geprüft werden, wenn ein chunks an seiner seite neu geladen wird.
        ```java
        ${visibility}
        ```

        ## Szenario 5 zufällige Weltgeneration
        Die Zufällige Weltgenerierung basiert auf der Perlin Noise Funktion dadurch wird die Höhe der Map an jeder x-y Position ermittelt.
        Die PERMUTATION_SIZE wird verwendet um koordinaten eine Höhe zu zuweisen.
        Die Fade, Lerp und Grad Methode rundet die Welt ab.
        Wenn der seed nich zur ersellung der Noise nicht geändert wird, wird die Welt auch wieder gleich generiert.
        ```java
        ${worldGen}
        ```

        Beim Generieren eines neuen Chunks wird die höhe für jede x-y position mit der generateHeight Methode ermittelt.
        Der oberste Block wird mit Grass belegt, die 3 Blöcke darunter mit Erde und der Rest mit Stein.
        ```java
        ${chunkGen}
        ```

        ## Funktionen Über das Funktionsversprechen hinaus
        Kollisionen habe ich bereits in Szenario 2 Steuerung dokumentiert.

        ich gehe davon aus dass es in diesem Projekt hauptsächlich um den java code
        geht in dem ich auch dis logik umgesetzt habe daher bin ich recht wenig auf den js code eingegangen.
        """, Map.of(
            "visibility", Text.cutOut("./views/WebGL/Game.java", "// visibility"),
            "worldGen", Text.cutOut("./views/WebGL/Game.java", "// Random world generation"),
            "chunkGen", Text.cutOut("./views/WebGL/Game.java", "// random generate new chunk")
        )
    )
);
