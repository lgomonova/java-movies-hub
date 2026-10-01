package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private int nextId = 1;

    public synchronized Movie add(Movie movie) {
        movie.setId(nextId++);
        movies.put(movie.getId(), movie);
        return movie;
    }

    public synchronized List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public synchronized List<Movie> getByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie m : movies.values()) {
            if (m.getYear() == year) {
                result.add(m);
            }
        }
        return result;
    }

    public synchronized Movie getById(int id) {
        return movies.get(id);
    }

    public synchronized boolean delete(int id) {
        return movies.remove(id) != null;
    }

    public synchronized void clear() {
        movies.clear();
        nextId = 1;
    }
}