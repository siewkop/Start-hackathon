# Narysuj swoją przygodę — wersja 1.2

## Obsługa

1. Znajdź okolicę na mapie lub użyj lokalizacji. Naciśnij **Narysuj trasę**. Przy zbyt odległym widoku aplikacja przybliży wybrany obszar.
2. Rysuj jednym palcem. Możesz podnieść palec i kontynuować od końca szkicu. Niebieska przerywana linia pokazuje zamierzony przebieg.
3. **Przytrzymaj palec przez chwilę**, aby zostawić większą kropkę postoju. Pojawi się pomarańczowy znacznik i potwierdzenie dotykowe, jeśli urządzenie obsługuje wibracje. Postój możesz dodać podczas rysowania albo przytrzymując palec na już narysowanej linii.
4. Strzałka cofania usuwa ostatni fragment lub ostatnio dodany postój. Wyłączenie zaznaczenia **Rysowanie** pozwala zwyczajnie przesuwać mapę; w trybie rysowania dostępne są też gesty dwoma palcami.
5. Rozwiń panel, aby wybrać profil pieszy/rowerowy, **Miks / Przyroda / Historia** i limit dodatkowej drogi (domyślnie 25%).
6. Naciśnij **Znajdź ciekawą trasę** albo **Wyznacz** nad mapą. Zielona linia jest faktycznie wyznaczoną trasą. Postoje pozostają oznaczone numerami; kolorowe znaczniki z literami pokazują wybrane miejsca z OSM po odpowiednim przybliżeniu mapy.
7. Rozwiń panel wyniku: zobaczysz uzasadnienie wyboru i listę **Wybrane dla Ciebie**. Kliknięcie znacznika lub pozycji listy pokazuje kategorię, przybliżoną odległość od trasy i link do źródła OpenStreetMap.

Wynik można zapisać i ponownie otworzyć. GPX zawiera geometrię trasy oraz punkty `wpt` dla postojów. Czas podawany przez silnik tras nie obejmuje czasu spędzonego na postojach.

## Podłączenie GPT

Klucz wpisz **lokalnie w terminalu backendu**. Nie wpisuj go w Android Studio ani w aplikacji telefonu i nie wklejaj go do czatu.

Zatrzymaj poprzedni backend przez Ctrl+C. W PowerShell, w głównym folderze projektu:

```powershell
$env:OPENAI_API_KEY = 'TWOJ_KLUCZ'
$env:OPENAI_MODEL = 'gpt-4.1-mini'
powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

Skrypt dziedziczy te zmienne. Dla telefonu przez Wi-Fi dodaj `-Lan`; przez USB i emulator użyj wcześniejszej konfiguracji adresu. Nie trzeba instalować nowej biblioteki Python — integracja korzysta z istniejącego klienta HTTP.

Bez klucza ta sama funkcja działa z oceną lokalną na podstawie danych OSM. Z kluczem backend używa [Responses API i Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses). Model można zmienić przez `OPENAI_MODEL` na obsługujący taki format.

`GET /health` pokazuje `version: 1.2.0` i `features.gpt`. Flaga GPT oznacza obecność klucza w konfiguracji, nie udane sprawdzenie uprawnień do modelu. Rzeczywiste użycie modelu potwierdza `discovery.ai_used: true` w wyniku oraz napis „wsparta GPT” w rozwiniętym panelu. Przy błędzie modelu wynik ma informację o użyciu oceny OSM.

Docker Compose przyjmuje te same zmienne z pliku `.env` w głównym folderze lub ze środowiska. Plik `.env` jest ignorowany przez Git.

## Jak wybierana jest trasa

- Szkic ma maksymalnie 2000 próbek, do 8 postojów i długość 80 m–120 km. To limit lokalnego prototypu, pozwalający utrzymać małe zapytania do usług publicznych.
- Backend redukuje linię metodą opartą na Douglasie–Peuckerze do maksymalnie 20 punktów prowadzących. Początek, koniec i indeksy postojów są zachowywane; długie proste odcinki dostają dodatkowe punkty prowadzące.
- OSRM wyznacza rzeczywistą trasę pieszą lub rowerową przez te punkty. To dopasowanie ogólnego przebiegu szkicu, nie rekonstrukcja śladu GPS ani gwarancja przejścia dokładnie po narysowanych pikselach.
- Atrakcje są pobierane z OSM przez Overpass w korytarzu szkicu. Przy awarii tego serwera używane są do sześciu ograniczonych promieniem zapytań Photon (do 24 wyników każde, wspólny limit czasu 22 s), ze wspólnym cache i ograniczeniem ruchu wyszukiwarki. Zapasowe źródło może zawierać mniej miejsc niż pełne zapytanie Overpass.
- Pod uwagę brane są m.in. oznaczone muzea, zabytki, punkty widokowe, parki, rezerwaty, szczyty i wodospady. Dane i ich kompletność zależą od OSM; nie dopisujemy fikcyjnych atrakcji, godzin otwarcia ani opisów historycznych.
- Porównywane są trasa dopasowana do szkicu i maksymalnie trzy dodatkowe warianty przez ciekawe miejsca. Objazd przekraczający wybrany limit długości jest odrzucany. Wariant nie może utracić obowiązkowego postoju ani nadmiernie oddalić się od szkicu.
- Ocena łączy liczbę i rodzaj pobliskich atrakcji, wybrany charakter wycieczki, dodatkowy dystans i zgodność ze szkicem. Szybkość nie jest jedynym kryterium.
- GPT dostaje maksymalnie 32 wstępnie wybrane miejsca: nazwy, kategorie, typy obiektów, dostępne opisy OSM, odnośniki Wikipedia oraz odległości od trasy. Zwraca wyłącznie kolejność tych identyfikatorów w kontrolowanym JSON. Nie dostaje klucza klienta ani pełnego szkicu. Nieznane, powtórzone lub niekompletne identyfikatory są odrzucane.

To wybór najlepszego z obliczonych wariantów według opisanej oceny, a nie dowód znalezienia najatrakcyjniejszej trasy ze wszystkich możliwych. Gdy objazdy nie poprawiają wyniku lub przekraczają limit, pozostaje trasa dopasowana do szkicu. Przy awarii obu źródeł atrakcji nadal zwracana jest prawdziwa trasa, z wyraźną informacją o braku oceny miejsc.

Postój musi leżeć blisko dostępnej ścieżki: backend odrzuca wynik oddalający się od niego o ponad 180 m. Punkty atrakcji są pokazywane w przybliżeniu do 200 m od wyznaczonej trasy; odległości są orientacyjne i nie oznaczają wejścia do obiektu ani gwarantowanego dostępu. Każde miejsce ma link do własnego rekordu OSM.

## API

```json
{
  "profile": "hike",
  "path": [
    {"lat": 52.2399, "lon": 21.0154},
    {"lat": 52.2423, "lon": 21.0145},
    {"lat": 52.2460, "lon": 21.0122},
    {"lat": 52.2490, "lon": 21.0100}
  ],
  "stop_indices": [1],
  "interest": "balanced",
  "max_extra_ratio": 0.25
}
```

Wyślij do `POST /api/v1/sketch-plans` z dotychczasowym nagłówkiem `X-Client-Token`. Indeks postoju wskazuje element listy `path`, licząc od zera. Odpowiedź to dotychczasowy model `Route`, rozszerzony o `discovery`: szkic, postoje, atrakcje, liczbę porównanych wariantów, dodatkowy dystans, uzasadnienie, ostrzeżenia i flagę użycia GPT. Zapis przez `/api/v1/routes` działa tak samo jak wcześniej. Stare trasy bez `discovery` pozostają obsługiwane.

Konfiguracja dostawców: `OSRM_FOOT_URL`, `OSRM_BIKE_URL`, `PHOTON_URL`, `OVERPASS_URL`. Dla większej liczby użytkowników ustaw docelowe instancje dostawców zamiast polegać na dostępności serwerów publicznych.

Dokumentacja źródeł: [OSRM](https://project-osrm.org/docs/v5.24.0/api/), [Overpass — zapytania wzdłuż linii](https://dev.overpass-api.de/overpass-doc/en/full_data/polygon.html), [Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md).


## Ulepszona selekcja atrakcji

Wyszukiwanie Overpass obejmuje korytarz 1,2 km i do 600 kandydatów, w tym ogrody, galerie, ruiny, forty, jaskinie i plaże. Przy mniej niż 12 wynikach uzupełnia je Photon. Zapasowe zapytania są rozmieszczone według długości szkicu, niezależnie od liczby próbek palca; na długich trasach sześć zapytań nie pokrywa każdego kilometra. Publiczne serwery mogą ograniczać wyniki.

Wynik zawiera najwyżej **8 polecanych miejsc** blisko faktycznie obliczonej trasy. Selekcja usuwa duplikaty obiektów, uwzględnia preferencje, dostępny opis i różnorodność kategorii oraz położenia. Kolejne podobne obiekty w jednym skupisku mają mniejszy wpływ na wybór przebiegu. Ranking GPT wpływa na wybór objazdów i końcowej ósemki. Nadal obowiązują limit objazdu, postoje oraz porównanie najwyżej czterech wariantów.

Znaczniki i lista używają tych samych kolorów: **H — historia (brąz), N — natura (niebieski), W — widoki (bursztyn), M — muzea (fiolet), S — sztuka (róż)**. Kliknięcie pokazuje tytuł, kategorię, odległość i opis, jeśli źródło go udostępnia. Link do Wikipedii pojawia się wyłącznie przy istniejącym oznaczeniu w OSM. Opisy nie są generowane przez model; ich dostępność zależy od danych, a Photon zwykle ich nie zwraca.

Dokumentacja źródeł: [opisy OSM](https://wiki.openstreetmap.org/wiki/Key:description), [API Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md). Nie wymaga to klucza Google Maps. Aby zobaczyć nową selekcję, uruchom ponownie backend, zainstaluj aktualne APK i wyznacz trasę ponownie; zapisane wcześniej wyniki zachowują stare dane.


## Nowa trasa, odnogi i rekreacja

Przycisk **Nowa trasa** pod podsumowaniem usuwa bieżący szkic, punkty, postoje i wynik, zachowując zapisane wycieczki. Powrót do zakładki „Zaplanuj” po zapisaniu/otwarciu zapisanej trasy rozpoczyna nowy szkic. W zwykłym widoku jest jeden przycisk obliczania na dole; górny „Wyznacz” pozostaje tylko w trybie pełnoekranowym, w którym dolny panel jest ukryty.

Suwak stylu działa dla szkiców i dwóch punktów. „Bezpośrednio” używa dotychczasowego OSRM, kolejne poziomy używają BRouter: hiking-beta dla spaceru (z ograniczeniem SAC do T1) lub trekking dla roweru. Profil rowerowy premiuje drogi rowerowe, na wyższych poziomach uwzględnia otoczenie rzek, lasów i hałas. Profil pieszy premiuje ścieżki i szlaki. To preferencja na podstawie dostępnych tagów OSM, nie gwarancja bulwaru, widoku, bezpiecznego przejazdu lub pełnej separacji od ruchu samochodowego. Własny serwer można podać w `BROUTER_URL`; domyślnie używany jest publiczny https://brouter.de/brouter.

Limit dodatkowej drogi dla atrakcji jest liczony względem trasy bazowej wybranego profilu. Błąd serwera rekreacyjnego powoduje powrót do OSRM z widocznym komunikatem. Wybrany styl zapisuje się z trasą i jest przywracany podczas jej otwierania.

Kontrola geometrii usuwa rozpoznane odnogi przechodzące dokładnie tą samą drogą w obie strony. Nie wycina prawdziwych pętli ani dojść do obowiązkowych postojów. Celowo narysowany powrót jest zachowywany. To ostrożna korekta: nie każdy możliwy nawrót zostanie usunięty. Po korekcie dystans i szacowany czas są proporcjonalnie aktualizowane.

Źródła profili: https://brouter.de/brouter/profiles2/hiking-beta.brf i https://brouter.de/brouter/profiles2/trekking.brf. BRouter otrzymuje współrzędne punktów prowadzących tak jak dotychczasowy router. Klucz OpenAI nie jest potrzebny do profili rekreacyjnych.
