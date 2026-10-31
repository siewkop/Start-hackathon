# Weryfikacja — 30.10.2026

## Automatyczna

- Backend: **21 testów zakończonych powodzeniem**, na wersjach z `requirements-dev.txt`.
- Android: **`testDebugUnitTest lintDebug assembleDebug` — BUILD SUCCESSFUL**.
- Lint: brak błędów; ostrzeżenia o nowszych wersjach SDK/bibliotek i sugestie użycia KTX.
- APK debug: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Rzeczywiste usługi

Dla punktów `(52.2297, 21.0122)` i `(52.2400, 21.0250)` API FOSSGIS zwróciło:

| Profil | Dystans | Czas dostawcy | Wierzchołki geometrii |
| --- | --- | --- | --- |
| Pieszo | 1936,3 m | 1549,0 s | 175 |
| Rowerem | 2125,8 m | 655,2 s | 182 |

Photon zwrócił wyniki wyszukiwania Warszawy. Publiczne usługi nie są częścią testów automatycznych.

## Emulator

Emulator API 36, ekran 1080 × 2400. Sprawdzono instalację APK, uruchomienie, wyświetlanie mapy, wybór startu i celu, rzeczywiste wyznaczenie trasy, zapis w backendzie, listę zapisanych tras, ponowne otwarcie oraz zachowanie zapisu po restarcie aplikacji. Sprawdzono także wpisanie zapytania w wyszukiwarce i wybór wyniku.

Eksport GPX z aplikacji otworzył systemowe udostępnianie. Plik został odczytany z aplikacji i poprawnie sparsowany jako GPX 1.1: nazwa „Moja wycieczka”, 111 punktów trasy.

Przyznano systemowe uprawnienie lokalizacji i podano emulatorowi symulowany odczyt GPS. Aplikacja ustawiła punkt „Moja lokalizacja” jako start. Po zatrzymaniu backendu i ponownym uruchomieniu aplikacji sprawdzono dostęp do listy z lokalnego cache.

Fizyczny telefon i Docker nie były uruchamiane w tej weryfikacji. Projekt zawiera instrukcję instalacji przez Android Studio oraz uruchamiania backendu przez USB, Wi-Fi i Docker.

## Wersja 1.1 — duża mapa

- `testDebugUnitTest lintDebug assembleDebug`: BUILD SUCCESSFUL, lint bez błędów.
- Sprawdzono widok początkowy całej Polski, przyciski zoomu, zwijanie i rozwijanie panelu, pełny ekran oraz wyjście przyciskiem Wstecz.
- Sprawdzono telefon 1080 × 2400, obrót do 2400 × 1080 i symulowany tablet 1600 × 2560 z gęstością 240 dpi. Po testach przywrócono oryginalny rozmiar, gęstość i automatyczny obrót emulatora.
- Na szerokim ekranie planowanie i nawigacja znajdują się po lewej stronie; na telefonie panel można zwinąć. Tryb pełnoekranowy ukrywa oba panele nawigacji.
- Wyznaczono rzeczywistą trasę pieszą w rejonie Łodzi: 7,2 km, szacowany czas 1 h 36 min.
- Sprawdzono, że „Polska” zachowuje wyznaczoną trasę, „Pokaż całą trasę” przywraca jej dopasowanie, a pełny ekran zachowuje geometrię i punkty.
- Podglądy: [telefon](preview.png), [pełny ekran](preview-fullscreen.png), [poziomo](preview-landscape.png), [tablet](preview-tablet.png), [trasa](preview-route.png).

## Wersja 1.2 — trasa z rysunku

- Backend: **42 testy zakończone powodzeniem**. Obejmują redukcję szkicu i pętle, zachowanie postojów, niezmienność celu przy objeździe do atrakcji, limit dodatkowej drogi, awarię obu źródeł miejsc, źródło zapasowe Photon, zapis metadanych, GPX z postojami oraz kontrolę identyfikatorów zwróconych przez GPT.
- Android: `testDebugUnitTest lintDebug assembleDebug` — **BUILD SUCCESSFUL**, lint bez błędów.
- Na emulatorze palcem zasymulowanym przez gesty ADB narysowano linię w dwóch fragmentach. Dodano dwa postoje przez przytrzymanie, w tym jeden na środku już istniejącego szkicu. Cofnięcie ostatniego postoju zmniejszyło licznik z dwóch do jednego; ponowne przytrzymanie przywróciło dwa.
- Pełny przepływ aplikacja → backend → rzeczywisty router: narysowany szkic 54 próbek, dwa postoje, trasa około 1,3 km / 17 min i 16 pobliskich miejsc OSM. Sprawdzono zapis w oddzielnej bazie demo i ponowne otwarcie po aktualizacji APK.
- Końcowy APK po poprawce limitu odczytu HTTP odebrał ponownie obliczoną trasę 1,1 km / 14 min, dwa postoje i 15 miejsc. Początek i koniec pozostały zgodne ze szkicem. Natywny eksport poprawnie sparsowano jako GPX 1.1: dwa punkty `wpt` oraz 119 punktów śladu.
- Osobny szkic przez Stare Miasto w Warszawie zwrócił 20 miejsc i trzy poprawne warianty. Główny Overpass był przeciążony; rzeczywiste dane dostarczył zapasowy Photon, m.in. Muzeum Warszawy i skwery.
- Integrację GPT sprawdzono na kontrolowanych odpowiedziach HTTP, łącznie z odrzuceniem nieznanych identyfikatorów. **Nie wykonywano płatnego wywołania GPT**: użytkownik nie przekazał klucza do tego uruchomienia. Dane atrakcji i geometria w teście na żywo pochodziły z rzeczywistych usług OSM.
- Sprawdzono rozwinięcie listy „Po drodze” i otwarcie szczegółów Kolumny Zygmunta, z odległością od śladu i linkiem do OSM.
- Podglądy: [szkic i postoje](preview-sketch.png), [dopasowana trasa](preview-sketch-route.png), [szczegóły miejsca](preview-sketch-place.png).


## Ulepszona selekcja atrakcji — 31.10.2026

- 48 testów backendu zakończonych powodzeniem, w tym pokrycie środka długiego odcinka, scalanie duplikatów, różnorodność krótkiej listy, opisy OSM, nowe kategorie i wpływ zweryfikowanych identyfikatorów GPT.
- Android: `testDebugUnitTest lintDebug assembleDebug` — BUILD SUCCESSFUL. Nowe metadane mają wartości domyślne, zachowując możliwość odczytu starszych zapisów.
- Integrację GPT sprawdzono przy użyciu kontrolowanych odpowiedzi HTTP. Nie wykonywano płatnych wywołań modelu w tej weryfikacji.
- Próba rzeczywistego wyszukiwania dla szkicu w Warszawie: 72 kandydatów, osiem polecanych miejsc, cztery kategorie. Zapasowe dane nie zawierały opisów ani odnośników Wikipedia; nie dopisywano ich automatycznie.
