package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    private static final int MIN_YEAR = 1888;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final String PREFIX = "/movies/";

    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if (path.equals("/movies") || path.equals(PREFIX)) {
                if (method.equalsIgnoreCase("GET")) {
                    handleGetAll(ex);
                } else if (method.equalsIgnoreCase("POST")) {
                    handlePost(ex);
                } else {
                    ex.sendResponseHeaders(405, -1);
                }
                return;
            }

            if (!path.startsWith(PREFIX)) {
                sendJson(ex, 404, gson.toJson(new ErrorResponse("Не найдено")));
                return;
            }

            String idPart = path.substring(PREFIX.length());
            if (idPart.contains("/")) {
                sendJson(ex, 404, gson.toJson(new ErrorResponse("Не найдено")));
                return;
            }

            if (method.equalsIgnoreCase("GET")) {
                handleGetById(ex, idPart);
            } else if (method.equalsIgnoreCase("DELETE")) {
                handleDelete(ex, idPart);
            } else {
                ex.sendResponseHeaders(405, -1);
            }
        } finally {
            ex.close();
        }
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        if (query == null) {
            sendJson(ex, 200, gson.toJson(store.getAll()));
            return;
        }
        Integer year = parseInt(query.startsWith("year=") ? query.substring(5) : null);
        if (year == null) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный параметр запроса — 'year'")));
            return;
        }
        sendJson(ex, 200, gson.toJson(store.getByYear(year)));
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendJson(ex, 415, gson.toJson(new ErrorResponse("Неподдерживаемый Content-Type")));
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Movie movie;
        try {
            movie = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            movie = null;
        }
        if (movie == null) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный JSON")));
            return;
        }

        int maxYear = Year.now().getValue() + 1;
        List<String> details = new ArrayList<>();
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            details.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_LENGTH) {
            details.add("название не должно быть длиннее " + MAX_TITLE_LENGTH + " символов");
        }
        if (movie.getYear() < MIN_YEAR || movie.getYear() > maxYear) {
            details.add("год должен быть между " + MIN_YEAR + " и " + maxYear);
        }
        if (!details.isEmpty()) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse("Ошибка валидации", details)));
            return;
        }

        Movie created = store.add(new Movie(0, movie.getTitle(), movie.getYear()));
        sendJson(ex, 201, gson.toJson(created));
    }

    private void handleGetById(HttpExchange ex, String idPart) throws IOException {
        Integer id = parseInt(idPart);
        if (id == null) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }
        Movie movie = store.getById(id);
        if (movie == null) {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
            return;
        }
        sendJson(ex, 200, gson.toJson(movie));
    }

    private void handleDelete(HttpExchange ex, String idPart) throws IOException {
        Integer id = parseInt(idPart);
        if (id == null) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }
        if (store.delete(id)) {
            sendNoContent(ex);
        } else {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
        }
    }

    private Integer parseInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}