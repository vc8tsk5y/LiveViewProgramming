Clerk.clear();
Clerk.markdown(
    Text.fillOut(
        """
        # Minecraft Klon

        _Tim Zechmeister_, _Technische Hochschule Mittelhessen_

        Matrikelnummer: _5442812_

        ## How to Play
        Nach der Initialisierung des LVP-Projekts kann das Spiel mit folgendem Befehl gestartet werden:
        ```java
        Game game = new Game();
        ```
        - Bewegung: mit W, A, S, D, und Leertaste steuert man die Spielfigur.
        - Umschauen: Durch einen Linksklick wird der Mauszeiger gesperrt, um sich umsehen zu können.
        - Mit Rechts- und Linksklick können blöcke platziert und abgebaut werden
        - Mit 1, 2, 3, kann man zwischen Blocktypen Stein, Gras und Erde wechseln.

        ## Szenario 1: Struktur der Welt
        Mit folgender Methode die bei der Initialisierung von Game aufgerufen wird,
        werden die Nötigen Java Scirpt Datein geladen, ein Canvas (der im Java Script code immer an die Browser größe angepasst wird) erstellt
        und das WebGL Objekt wird initialisiert.
        Sobld die Texturen geladen sind wird die Welt generiert und Methoden zur Steurung werden ausgeführt.
        ```java
        ${initWebgl}
        ```

        Darauf wie Blöcke gespeichret werden gehe ich in Szenario 4 Welt in chunks teilen weiter ein.
        Grundlegend werden Blöcke in einem 3D-Array aus Luft, Stein, Gras, Erde gespeichert.
        ```java
        ${BlockType}
        ```
        Grundlegende Methoden sind:
        ```java
        ${basics}
        ```
        Java Script
        ```js
        ${basicsjs}
        ```

        ## Szenario 2 Steuerung
        Bei des Steuerung des Spielers werden Eingabedaten von Java Script an Java geschickt wo die Spilerposition und Blickrichtung aktualisiert werden und zurück geschickt werden.
        - Der Java Script teil sendet beim drücken einer Taste ein `KeyDown` und `KeyUp` um gedrückthalten mehrerer Tasten zu ermöglichen.
        - Maus Bewegungen werden aus Performanzgründen ertmals zusammen addiert und aufeinmal (maximal 60 mal die Sekunde) Gesendet.
        ```js
        ${mnKEventjs}
        ```

        In Java lege ich Vektoren für die Spielerposition und die Blickrichtung sowie Winkel der horizontalen und vertikalen Rotation an.
        ```java
        ${playerMovement}
        ```

        In Java wandle ich die ankommenden json daten in brauchbare Srings um.
        Für Maus Bewegungen berechne ich den Blickrichtungs Vektor aus den zuvorberechneten horizontalen und vertikalen Winkeln.
        ```java
        ${mouseEventjava}
        ```

        Für Links- und Rechtsklicks wird eine Linie von der Kameraposition des Spielers
        zu dem Nächsten Block in 5 Blöcken entfernung mit dem Amanatides-Woo raycasting Algorithmus berechnet.
        Diese Methode gibt entweder die Position des Getroffenen Blocks zurück,
        um diesen dann Abzubauen oder die Position des Blocks der zuletzt von dem algorithmus durchlaufen wurde
        um die anliegenden Blockposition zum plazieren eines neuen Blocks zu erhalten.
        ```java
        ${raycasting}
        ```

        Ein Rate-Limiter sorgt dafür, dass die Spielerposition in festen Intervallen aktualisiert wird.
        Alle momentan gedrückten tasten werden in dem activeKeys Set gespeichert.
        Ich gehe in einem späteren Abschnitt darauf ein was passiert wenn die Leertaste in diesem Set enthalten ist.
        Wenn 1, 2, oder 3 enthalten ist wird der ausgewählte Blocktyp geändert.
        Wenn W, A, S, und/oder D im Set enthalten sind wird ein Vektor in die entsprechende Richtung angelegt der normalisiert und mit der Bewegungsgeschwindigkeit skaliert wird.
        ```java
        ${clickEventjava}
        ```

        Der Bewegungsvektor wird auf Kollisionen getestet und angepasst.
        ```java
        ${collision}
        ```

        """, Map.of(
            "initWebgl", Text.cutOut("./views/WebGL/Game.java", "// initialize webgl"),
            "BlockType", Text.cutOut("./views/WebGL/BlockType.java", "// BlockType"),
            "basics", Text.cutOut("./views/WebGL/Game.java", "// basics"),
            "basicsjs", Text.cutOut("./views/WebGL/webGL.js", "// basics"),
            "mnKEventjs", Text.cutOut("./views/WebGL/handleMnKEvent.js", "// mnKEvent"),
            "playerMovement", Text.cutOut("./views/WebGL/Game.java", "// Player movement"),
            "mouseEventjava", Text.cutOut("./views/WebGL/Game.java", "// mouseEvent"),
            "clickEventjava", Text.cutOut("./views/WebGL/Game.java", "// clickEvent"),
            "raycasting", Text.cutOut("./views/WebGL/Game.java", "// raycasting"),
            "collision", Text.cutOut("./views/WebGL/Game.java", "// collision")
        )
    )
);

Clerk.markdown(
    Text.fillOut(
        """
        Mit der updateCamera Methode sende ich maximal 60 mal die Sekunde ein Update der Spielerposition und der Winkel der horizontalen und vertikalen Rotation.
        ```java
        ${updateCamera}
        ```
        in Java Script wird die Kameraposition und Blickrichtung aktualisiert.
        ```js
        ${updateCamerajs}
        ```

        ## Szenario 4 Welt in chunks teilen
        Jeder Chunk ist 16x256x16 Blöcke groß. Die Blöcke der Chunks sind zwar permanent für java einzusehen,
        sie werden aber erst wenn der Spieler in die Nähe kommt an den WebGL Java Script teil geschickt der sie für den Benutzer sichtbar macht, um Performance zu steigern.
        Falls der Chunks noch nicht in Java existiert wird er mit einem zufälligen Terrain generiert aber dazu in Szenario 5 zufällige Weltgeneration mehr.
        Die load Methode sendet alle zu ladenden Blöcke an den WebGL teil.
        Die unload als gegenstück zur load sendet alle zu entfernenden Blöcke an den WebGL teil.
        ```java
        ${chunks}
        ```

        Alle Chunks werden in einer Hashmap gespeichert als Key wird ein Hash aus der Position des Chunks berechnet.
        Die handleChunkRendering Methode erstellt und updated Sets von geladenen, zu ladenden und zu entladenden Chunks und lässt diese auch immer laden oder entladen.
        (auf den Part mit dem neuladen der Chunkränder gehe ich in Szenario 3 performantes Rendering noch ein).
        ```java
        ${chunkutil}
        ```

        ## Szenario 3 performantes Rendering
        Aus Performanzgründen werden nur die Blöcke gerendert.
        Blöcke die nicht geladen werden haben keine Seite, die mit Luft in Kontakt ist das ist anders als im Funktionsversprechen, nicht mit einer Voxel grid.
        Die areaReload Methode wird benutzt um neu zu berechnen welche Blöcke in einem Bereich sichtbar sind zum Beispiel wenn Blöcke plaziert oder abgebaut werden.
        Die reloadChunkEdge Methode ist recht spezifisch da Blöcke an rändern von Chunks nur sichtbar sind wenn der anliegende Chunk noch nicht existiert
        diese seite soll neu geprüft werden, wenn ein Chunk an seiner seite neu geladen wird.
        ```java
        ${visibility}
        ```

        ## Szenario 5 zufällige Weltgeneration
        Die Weltgenerierung basiert auf der Perlin-Noise-Funktion, die Höhen für jede x-y-Position berechnet.
        Die Fade, Lerp und Grad Methode rundet die Welt ab.
        ```java
        ${worldGen}
        ```

        Beim Generieren eines neuen Chunks wird die höhe für jede x-y position mit der generateHeight Methode ermittelt.
        Der oberste Block wird mit Gras belegt, die 3 Blöcke darunter mit Erde und der Rest mit Stein.
        ```java
        ${chunkGen}
        ```

        ## Funktionen die Über das Funktionsversprechen hinaus gehen
        Kollisionen habe ich bereits in Szenario 2 Steuerung dokumentiert.\n
        Außerdem habe ich gravitation eingebaut, die den Spieler nach unten beschleunigt.
        Beim drücken der Leertaste wird die Gravitation auf einen Positiven Wert gesetzt um den Spiler nach oben zu beschleunigen wenn er auf dem Boden ist.
        um recht genau zu überprüfen ob der Spieler auf dem Boden ist wird der Spiler bei Kollisionen mit dem Boden genau auf den Boden teleportiert.
        ```java
        ${gravity}
        ```
        """, Map.of(
            "updateCamera", Text.cutOut("./views/WebGL/Game.java", "// udpate camera"),
            "updateCamerajs", Text.cutOut("./views/WebGL/webGL.js", "// udpate camera"),
            "chunks", Text.cutOut("./views/WebGL/Game.java", "// chunks"),
            "chunkutil", Text.cutOut("./views/WebGL/Game.java", "// chunkutil"),
            "visibility", Text.cutOut("./views/WebGL/Game.java", "// visibility"),
            "worldGen", Text.cutOut("./views/WebGL/Game.java", "// Random world generation"),
            "chunkGen", Text.cutOut("./views/WebGL/Game.java", "// random generate new chunk"),
            "gravity", Text.cutOut("./views/WebGL/Game.java", "// gravity")
        )
    )
);
