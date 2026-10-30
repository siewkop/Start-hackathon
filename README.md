# Trail Pocket

Natywna baza aplikacji turystycznej inspirowanej podstawowym przepływem Komoota: **start → cel → trasa → zapis**. Własny interfejs, polskie teksty, osobny backend i rzeczywiste wyznaczanie tras po drogach oraz ścieżkach. Projekt przygotowany do dalszego rozwijania na hackathonie Occam Award.

## Co jest gotowe

- Rysowanie trasy palcem: szkic, postoje przez przytrzymanie, cofanie fragmentu lub postoju.
- Dopasowanie szkicu do dróg i ścieżek oraz wybór ciekawszego wariantu z limitem dodatkowej drogi.
- Rzeczywiste atrakcje OpenStreetMap na mapie i na liście; opcjonalny ranking GPT w backendzie.
- Duża mapa OpenStreetMap, uruchamiana od widoku całej Polski; można przeglądać także pozostałe kraje.
- Tryb pełnoekranowy, przyciski przybliżania/oddalania, powrót do widoku Polski i dopasowanie całej trasy.
- Zwijany panel na telefonie oraz boczny panel na szerokim ekranie i w poziomie.
- Wybór startu i celu dotknięciem mapy lub przez wyszukiwarkę miejsc.
- Trasy piesze i rowerowe z geometrią, dystansem i szacowanym czasem.
- Ustawienie bieżącej lokalizacji jako startu, po przyznaniu zgody.
- Zapis pod własną nazwą, lista tras, ponowne otwieranie i usuwanie.
- Eksport i udostępnianie GPX, również z lokalnie zapamiętanych tras.
- Zmiana adresu serwera i test połączenia z poziomu aplikacji.
- REST API, SQLite, cache usług zewnętrznych, Docker i testy.

Zakres to **planer tras**, bez nawigacji głosowej, nagrywania aktywności, przewyższeń, kont społecznościowych i pobierania regionów offline. Ostatnio pobrane zapisane trasy są dostępne z lokalnego cache; nowe planowanie i wyszukiwanie potrzebują internetu i backendu. Aplikacja nie jest powiązana z Komootem.

Instrukcja nowej funkcji, algorytm i podłączenie klucza GPT: **[Rysowanie tras i postoje](docs/SKETCH-ROUTES.md)**. Po aktualizacji do wersji 1.2 uruchom backend ponownie, aby załadować nowy endpoint.

## Najkrótsze uruchomienie: Windows + telefon USB

Wymagania: Android Studio z obsługą AGP 8.9.2, Android SDK 35, JDK 17 lub 21 i Python 3.11–3.13. Android 8.0+ (API 26). Gradle Wrapper jest w repozytorium; nie trzeba instalować Gradle osobno. Pierwszy build pobiera zależności z internetu.

1. W terminalu PowerShell, w głównym folderze projektu, uruchom backend:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
   ```

   Skrypt tworzy lokalne `.venv`, instaluje zależności i uruchamia serwer. Zostaw ten terminal otwarty. Sprawdź [stan serwera](http://127.0.0.1:8000/health) i [dokumentację API](http://127.0.0.1:8000/docs).

2. W Android Studio wybierz **Open** i wskaż podfolder **`android`**. Zaczekaj na Gradle Sync. W **Settings → Build Tools → Gradle → Gradle JDK** wybierz wbudowany JDK 17/21. Zainstaluj SDK Platform 35 i Build Tools 35.0.0, jeżeli IDE o to poprosi.

3. W telefonie włącz Opcje programisty i Debugowanie USB. Podłącz telefon, zatwierdź komputer i wybierz urządzenie w Android Studio.

4. W terminalu Android Studio uruchom przekierowanie portu:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
   ```

   Jeśli masz kilka urządzeń, dodaj `-s NUMER_URZĄDZENIA` przed `reverse`. Po ponownym podłączeniu USB przekierowanie może wymagać powtórzenia.

5. Naciśnij **Run ▶** dla modułu `app`. W aplikacji otwórz koło zębate, ustaw **`http://127.0.0.1:8000`**, zapisz adres i sprawdź połączenie.

6. Dotknij mapy, aby wybrać start, a następnie cel. Wybierz **Pieszo** lub **Rowerem** i **Wyznacz trasę**. Zapisz trasę, sprawdź zakładkę **Moje trasy** i eksport GPX.

Panel planowania rozwiniesz strzałką nad przyciskiem „Wyznacz trasę” — tam zmienisz profil i wyszukasz lub zamienisz punkty. Przycisk z ikoną pełnego ekranu w prawym górnym rogu mapy ukrywa panele i nawigację. Wrócisz przez „Planer” lub systemowy przycisk Wstecz. „Polska” dopasowuje widok całego kraju bez usuwania wybranych punktów ani trasy. Mapa pobiera widoczny obszar online; projekt nie pobiera całej Polski do użytku offline.

## Emulator i Wi-Fi

| Sposób | Adres w aplikacji | Backend |
| --- | --- | --- |
| Emulator Android Studio | `http://10.0.2.2:8000` (domyślny) | standardowy skrypt |
| Telefon USB + `adb reverse` | `http://127.0.0.1:8000` | standardowy skrypt |
| Telefon i komputer w jednej sieci Wi-Fi | np. `http://192.168.1.10:8000` | skrypt z flagą `-Lan` |
| Zdalny serwer | `https://twoja-domena` | backend za HTTPS |

Dla Wi-Fi uruchom:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -Lan
ipconfig
```

Użyj IPv4 aktywnej karty Wi-Fi komputera. Windows Firewall musi pozwalać Pythonowi na połączenia przychodzące w sieci prywatnej. Sieć hackathonowa może izolować urządzenia — wtedy użyj USB. `localhost` telefonu oznacza telefon, a `10.0.2.2` działa tylko w emulatorze.

## Samodzielna kompilacja APK

W Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Nazwa polecenia może nieznacznie różnić się między wersjami IDE.

Albo w PowerShell, z folderu `android`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

Plik wynikowy: `android/app/build/outputs/apk/debug/app-debug.apk`. Możesz go zainstalować przez Android Studio, otworzyć na telefonie lub użyć `adb install -r app-debug.apk`. Wersja debug dopuszcza HTTP do lokalnego backendu; release wymaga HTTPS. Podpisywanie release konfiguruje się własnym kluczem w Android Studio.

## Backend ręcznie lub w Dockerze

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend\requirements.txt
cd backend
..\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 1
```

Linux/macOS: użyj `.venv/bin/python` zamiast `.venv\Scripts\python.exe`.

Z głównego folderu możesz też uruchomić:

```text
docker compose up --build
```

SQLite jest zapisywany w `backend/data/trail.db`, a w Dockerze w trwałym woluminie `trail-data`. Docker wystawia port 8000 także do sieci lokalnej. Backend działa jako jeden proces, ponieważ limit usług zewnętrznych jest wspólny dla żądań w tym procesie. Nie zwiększaj liczby workerów ani replik bez przeniesienia ogranicznika ruchu do wspólnego magazynu.

Zmienne konfiguracyjne są w `backend/.env.example`. Uruchomienie przez Python czyta zmienne środowiskowe, **nie ładuje pliku `.env` automatycznie**. Docker Compose odczytuje `.env` w głównym folderze. Przykład PowerShell przed uruchomieniem skryptu:

```powershell
$env:OSRM_FOOT_URL = 'https://twoj-router-pieszy'
$env:OSRM_BIKE_URL = 'https://twoj-router-rowerowy'
$env:PHOTON_URL = 'https://twoja-wyszukiwarka'
```

Adresy OSRM wskazują katalog usługi **przed** `/route/v1/driving/…`. W OSRM profil drogowy jest ustalany przy budowaniu grafu na serwerze. Słowo `driving` w ścieżce API nie przełącza pieszego serwera na samochodowy — aplikacja wybiera osobne instancje `routed-foot` i `routed-bike`.

## API i identyfikacja

Wszystkie endpointy `/api/v1` przyjmują nagłówek `X-Client-Token`: 64 małe znaki hex, losowane raz przez aplikację. Serwer przechowuje wyłącznie hash tokenu i ogranicza odczyt, zapis, GPX i usuwanie do właściciela. To prosty sekret urządzenia, bez rejestracji. Token działa jak hasło: nie umieszczaj go w logach. Odinstalowanie aplikacji lub wyczyszczenie jej danych usuwa token i dostęp do wcześniejszych tras; GPX możesz wyeksportować wcześniej.

| Metoda | Ścieżka | Działanie |
| --- | --- | --- |
| GET | `/health` | Stan lokalnego API, bez sprawdzania dostawców map |
| GET | `/api/v1/places?q=Warszawa` | Maks. 5 miejsc |
| POST | `/api/v1/plans` | Plan dla profilu `hike` lub `bike` |
| POST | `/api/v1/sketch-plans` | Dopasowanie szkicu, zachowanie postojów i atrakcyjniejszy wariant |
| POST | `/api/v1/routes` | Zapis planu przez `plan_id` i `name` |
| GET | `/api/v1/routes` | Trasy urządzenia |
| GET | `/api/v1/routes/{id}` | Szczegóły trasy |
| DELETE | `/api/v1/routes/{id}` | Usunięcie trasy |
| GET | `/api/v1/routes/{id}/gpx` | GPX z backendu |

Plan można zapisać przez 24 godziny. Ponowienie zapisu tego samego planu zwraca ten sam rekord. Geometrię i statystyki ustala backend; klient przy zapisie przesyła tylko nazwę i identyfikator planu. GeoJSON używa kolejności **`[długość, szerokość]`**, pola punktów mają jawne `lat` i `lon`.

Przykładowe wyznaczenie trasy w PowerShell:

```powershell
$headers = @{ 'X-Client-Token' = ('a' * 64) } # Wyłącznie token do lokalnego testu.
$body = @{
  profile = 'hike'
  points = @(
    @{ lat = 52.2297; lon = 21.0122; name = 'Start' },
    @{ lat = 52.2400; lon = 21.0250; name = 'Cel' }
  )
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri 'http://127.0.0.1:8000/api/v1/plans' -Method Post -Headers $headers -ContentType 'application/json' -Body $body
```

## Logika inteligentnej trasy

```text
android/app/src/main/java/pl/trailpocket/
  ui/TrailApp.kt           główna nawigacja, zapisane trasy i ustawienia
  ui/PlannerScreen.kt      duża mapa, pełny ekran i adaptacyjny panel planowania
  ui/SketchTouchHandler.kt gest palcem, przytrzymanie na postój, podgląd szkicu
  ui/DiscoveryUi.kt        kontrolki rysowania i szczegóły atrakcji
  ui/TrailMap.kt           mapa; punkt wymiany biblioteki mapowej
  PlannerViewModel.kt      stan i akcje użytkownika
  data/TrailRepository.kt  HTTP, sekret urządzenia, cache
  data/Models.kt           kontrakt z backendem
backend/app/
  main.py                 endpointy i zależności
  providers.py            adaptery routingu i wyszukiwania
  sketch.py               warianty szkicu, obowiązkowe postoje i wybór trasy
  geo.py                  geometria w metrach i redukcja punktów szkicu
  attractions.py          atrakcje OSM, źródło zapasowe i opcjonalny ranking GPT
  models.py               walidacja i modele API
  storage.py              trwały zapis w SQLite
```

Funkcja Occam Award to **szkic zamiast ręcznego dodawania przystanków**. Backend zachowuje kolejność rysunku i obowiązkowe postoje, a następnie porównuje dopasowaną trasę z maksymalnie trzema objazdami do rzeczywistych atrakcji. Wybiera oceniony wariant, przestrzegając limitu dodatkowej drogi. GPT może ustalić priorytet miejsc z dostarczonej listy; nie tworzy geometrii ani nazw nowych atrakcji. Klucz i wybór modelu pozostają w backendzie.

## Usługi mapowe i ograniczenia demonstracji

- Kafelki: [OpenStreetMap Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/). Aplikacja pokazuje atrybucję i link do poprawiania mapy, identyfikuje się własnym User-Agentem, używa cache i nie pobiera regionów masowo. Kafelki trafiają bezpośrednio do telefonu.
- Routing: [FOSSGIS](https://routing.openstreetmap.de/about.html), limit maksymalnie 1 żądania na sekundę. Backend współdzieli ogranicznik obu profili i cache na godzinę. Nie zamienia awarii routingu w fikcyjną linię prostą. Trasa może zaczynać się na najbliższej dostępnej drodze, a czas jest oszacowaniem dostawcy.
- Wyszukiwanie: [Photon](https://github.com/komoot/photon), publiczny serwer demonstracyjny dopuszcza rozsądną liczbę zapytań i nie gwarantuje dostępności. Szukamy dopiero po naciśnięciu przycisku, nie przy każdym znaku; backend ogranicza ruch i cache'uje wyniki na dobę.
- Mapa korzysta z [osmdroid 6.1.20](https://github.com/osmdroid/osmdroid), Apache 2.0. Projekt biblioteki jest zarchiwizowany. To prosta baza na hackathon; przy długoterminowym rozwoju zaplanuj wymianę adaptera `TrailMap.kt` na utrzymywaną bibliotekę, np. MapLibre.

To konfiguracja do lokalnego prototypu i małego demo. Przed publicznym udostępnieniem potrzebne są HTTPS, limit ruchu na wejściu, docelowi dostawcy map i logowanie z odzyskiwaniem konta. Nie wysyłaj sekretów AI do aplikacji. Punkty tras i wyszukiwane nazwy są przekazywane zewnętrznym dostawcom; aplikacja informuje o tym w ustawieniach.

## Weryfikacja

Backend (z głównego folderu):

```powershell
.\.venv\Scripts\python.exe -m pip install -r backend\requirements-dev.txt
cd backend
..\.venv\Scripts\python.exe -m pytest tests -q
```

Android (z folderu `android`):

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Testy backendu nie korzystają z publicznych serwerów. Sprawdzają pełny przepływ plan–zapis–GPX–usuwanie, izolację właścicieli, trwałość SQLite, walidację, cache, poprawny wybór profilu oraz błędy dostawców. Test Androida sprawdza zgodność kontraktu JSON, kolejność współrzędnych oraz formatowanie czasu i dystansu.

Ręczny test na telefonie: mapa i oba profile → wyszukiwanie → zgoda/odmowa lokalizacji → zapis i ponowne uruchomienie → otwarcie trasy → GPX → wyłączenie backendu i podgląd cache → ponowne włączenie → usunięcie. Osobno sprawdź mały ekran i obrót urządzenia.

Wyniki wykonanych sprawdzeń znajdują się w [docs/VERIFICATION.md](docs/VERIFICATION.md).
