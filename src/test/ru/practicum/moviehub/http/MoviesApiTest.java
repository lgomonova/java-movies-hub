package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final String JSON = "application/json; charset=UTF-8";
    private static final MoviesStore store = new MoviesStore();
    private static final Gson gson = new Gson();
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType)
            throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .method(method, publisher);
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send("GET", path, null, null);
    }

    private HttpResponse<String> post(String body) throws Exception {
        return send("POST", "/movies", body, JSON);
    }

    private List<Movie> parseList(String body) {
        return gson.fromJson(body, new ListOfMoviesTypeToken().getType());
    }

    private void assertJsonContentType(HttpResponse<String> resp) {
        assertEquals(JSON, resp.headers().firstValue("Content-Type").orElse(""),
                "Content-Type должен содержать формат данных и кодировку");
    }

    private void assertHasError(HttpResponse<String> resp) {
        JsonObject obj = gson.fromJson(resp.body(), JsonObject.class);
        assertTrue(obj.has("error"), "Ответ должен содержать поле error");
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = get("/movies");
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertJsonContentType(resp);
        assertTrue(parseList(resp.body()).isEmpty(), "Ожидается пустой массив фильмов");
    }

    @Test
    void getMovies_returnsAddedMovies() throws Exception {
        store.add(new Movie(0, "Матрица", 1999));
        store.add(new Movie(0, "Зеркало", 1975));

        HttpResponse<String> resp = get("/movies");
        assertEquals(200, resp.statusCode());
        assertJsonContentType(resp);
        assertEquals(2, parseList(resp.body()).size());
    }

    @Test
    void postMovie_validData_returns201() throws Exception {
        HttpResponse<String> resp = post("{\"title\":\"Матрица\",\"year\":1999}");
        assertEquals(201, resp.statusCode());
        assertJsonContentType(resp);
        Movie created = gson.fromJson(resp.body(), Movie.class);
        assertEquals(1, created.getId());
        assertEquals("Матрица", created.getTitle());
        assertEquals(1999, created.getYear());
    }

    @Test
    void postMovie_emptyTitle_returns422() throws Exception {
        HttpResponse<String> resp = post("{\"title\":\"\",\"year\":1999}");
        assertEquals(422, resp.statusCode());
        assertJsonContentType(resp);
        assertHasError(resp);
    }

    @Test
    void postMovie_tooLongTitle_returns422() throws Exception {
        HttpResponse<String> resp = post("{\"title\":\"" + "a".repeat(101) + "\",\"year\":1999}");
        assertEquals(422, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void postMovie_yearTooSmall_returns422() throws Exception {
        HttpResponse<String> resp = post("{\"title\":\"Фильм\",\"year\":1887}");
        assertEquals(422, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void postMovie_yearTooBig_returns422() throws Exception {
        int year = Year.now().getValue() + 2;
        HttpResponse<String> resp = post("{\"title\":\"Фильм\",\"year\":" + year + "}");
        assertEquals(422, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void postMovie_wrongContentType_returns415() throws Exception {
        HttpResponse<String> resp = send("POST", "/movies",
                "{\"title\":\"Фильм\",\"year\":1999}", "text/plain");
        assertEquals(415, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void postMovie_invalidJson_returnsError() throws Exception {
        HttpResponse<String> resp = post("{не json");
        assertEquals(400, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void getMovieById_existing_returnsMovie() throws Exception {
        Movie m = store.add(new Movie(0, "Матрица", 1999));
        HttpResponse<String> resp = get("/movies/" + m.getId());
        assertEquals(200, resp.statusCode());
        assertJsonContentType(resp);
        assertEquals("Матрица", gson.fromJson(resp.body(), Movie.class).getTitle());
    }

    @Test
    void getMovieById_notFound_returns404() throws Exception {
        HttpResponse<String> resp = get("/movies/999");
        assertEquals(404, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void getMovieById_notNumber_returns400() throws Exception {
        HttpResponse<String> resp = get("/movies/abc");
        assertEquals(400, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void deleteMovie_existing_returns204() throws Exception {
        Movie m = store.add(new Movie(0, "Матрица", 1999));
        HttpResponse<String> resp = send("DELETE", "/movies/" + m.getId(), null, null);
        assertEquals(204, resp.statusCode());
        assertNull(store.getById(m.getId()));
    }

    @Test
    void deleteMovie_notFound_returns404() throws Exception {
        HttpResponse<String> resp = send("DELETE", "/movies/999", null, null);
        assertEquals(404, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void deleteMovie_notNumber_returns400() throws Exception {
        HttpResponse<String> resp = send("DELETE", "/movies/abc", null, null);
        assertEquals(400, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void getMoviesByYear_returnsMatching() throws Exception {
        store.add(new Movie(0, "Матрица", 1999));
        store.add(new Movie(0, "Зеркало", 1975));

        HttpResponse<String> resp = get("/movies?year=1999");
        assertEquals(200, resp.statusCode());
        assertJsonContentType(resp);
        List<Movie> movies = parseList(resp.body());
        assertEquals(1, movies.size());
        assertEquals("Матрица", movies.get(0).getTitle());
    }

    @Test
    void getMoviesByYear_noMatches_returnsEmptyList() throws Exception {
        store.add(new Movie(0, "Матрица", 1999));
        HttpResponse<String> resp = get("/movies?year=2000");
        assertEquals(200, resp.statusCode());
        assertTrue(parseList(resp.body()).isEmpty());
    }

    @Test
    void getMoviesByYear_notNumber_returns400() throws Exception {
        HttpResponse<String> resp = get("/movies?year=abc");
        assertEquals(400, resp.statusCode());
        assertHasError(resp);
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        assertEquals(405, send("PUT", "/movies", "{}", JSON).statusCode());
        assertEquals(405, send("PATCH", "/movies/1", "{}", JSON).statusCode());
    }
}