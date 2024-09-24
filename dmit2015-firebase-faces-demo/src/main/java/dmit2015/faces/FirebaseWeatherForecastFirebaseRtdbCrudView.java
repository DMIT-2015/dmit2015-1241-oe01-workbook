package dmit2015.faces;

import dmit2015.model.FirebaseWeatherForecast;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import lombok.Getter;
import lombok.Setter;
import net.datafaker.Faker;
import org.omnifaces.util.Messages;
import org.primefaces.PrimeFaces;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * This Jakarta Faces backing bean class contains the data and event handlers
 * to perform CRUD operations using a PrimeFaces DataTable configured to perform CRUD.
 * <p>
 * The Java HttpClient library is used send Http Request to the Firebase Realtime Database REST API.
 */
@Named("currentFirebaseWeatherForecastFirebaseRtdbCrudView")
@ViewScoped // create this object for one HTTP request and keep in memory if the next is for the same page
public class FirebaseWeatherForecastFirebaseRtdbCrudView implements Serializable {

    @Inject
    private FirebaseLoginSession _firebaseLoginSession;

    /**
     * The selected FirebaseWeatherForecast instance to create, edit, update or delete.
     */
    @Getter
    @Setter
    private FirebaseWeatherForecast selectedFirebaseWeatherForecast;

    /**
     * The unique name of the selected FirebaseWeatherForecast instance.
     */
    @Getter
    @Setter
    private String selectedId;

    /**
     * The list of FirebaseWeatherForecast objects fetched from the Firebase Realtime Database
     */
    @Getter
    private List<FirebaseWeatherForecast> firebaseWeatherForecasts;

    /**
     * The base URL to the Firebase Realtime Database
     */
    private static final String FIREBASE_REALTIME_DATABASE_BASE_URL = "https://dmit2015-1241-oe01-swu-default-rtdb.firebaseio.com";

    /**
     * The URL to the Firebase Realtime Database to access all data.
     */
    private String _jsonAllDataPath;

    /**
     * Fetch all FirebaseWeatherForecast from the Firebase Realtime Database
     */
    @PostConstruct
    public void init() {
        // Get the Firebase Authenticated userId and token.
        String firebaseUserId = _firebaseLoginSession.getFirebaseUser().getLocalId();
        String firebaseToken = _firebaseLoginSession.getFirebaseUser().getIdToken();

//        _jsonAllDataPath = String.format("%s/%s.json", FIREBASE_REALTIME_DATABASE_BASE_URL, FirebaseWeatherForecast.class.getSimpleName());
        _jsonAllDataPath = String.format("%s/%sOwner/%s.json?auth=%s",
                FIREBASE_REALTIME_DATABASE_BASE_URL,
                FirebaseWeatherForecast.class.getSimpleName(),
                firebaseUserId,
                firebaseToken);

        fetchFirebaseData();
    }

    /**
     * Event handler for the New button on the Faces crud page.
     * Create a new selected FirebaseWeatherForecast instance to enter data for.
     */
    public void onOpenNew() {
        selectedFirebaseWeatherForecast = new FirebaseWeatherForecast();
        selectedId = null;
    }

    /**
     * Use the DataFaker to generate random data.
     *
     * @link <a href="https://www.datafaker.net/documentation/getting-started/">Getting started with DataFaker</a>
     */
    public void onGenerateData() {
        try {
            var faker = new Faker();
            var randomGenerator = RandomGenerator.getDefault();
            selectedFirebaseWeatherForecast.setCity(faker.address().city());
            selectedFirebaseWeatherForecast.setDate(LocalDate.now().plusDays(randomGenerator.nextInt(1, 6)));
            selectedFirebaseWeatherForecast.setTemperatureCelsius(faker.number().numberBetween(-20,50));
            selectedFirebaseWeatherForecast.setDescription(faker.weather().description());

        } catch (Exception e) {
            Messages.addGlobalError("Error generating data {0}", e.getMessage());
        }

    }

    /**
     * Push or Write currentFirebaseWeatherForecast to Firebase Realtime Database using the REST API
     *
     * @link <a href="https://firebase.google.com/docs/reference/rest/database">Firebase Realtime Database REST API</a>
     */
    public void onSave() {
        // Get the Firebase Authenticated userId and token.
        String firebaseUserId = _firebaseLoginSession.getFirebaseUser().getLocalId();
        String firebaseToken = _firebaseLoginSession.getFirebaseUser().getIdToken();

        // Jsonb is used for converting Java objects to a JSON string or visa-versa
        // HttpClient is native Java library for sending Http Request to a web server
        try (Jsonb jsonb = JsonbBuilder.create();
             var httpClient = HttpClient.newHttpClient()) {

            // Convert the currentFirebaseWeatherForecast to a JSON string using JSONB
            String requestBodyJson = jsonb.toJson(selectedFirebaseWeatherForecast);

            // If selecteId is null then create new data otherwise update current data
            if (selectedId == null) {

                // Create an Http Request for sending an Http POST request to push new data
                var httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(_jsonAllDataPath))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                        .build();
                // Send the Http Request
                var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                // Check if the Http Request response is successful
                if (httpResponse.statusCode() == 200) {
                    // Get the body of the Http Response
                    var responseBodyJson = httpResponse.body();
                    // Convert the JSON String to a JsonObject
                    JsonObject responseJsonObject = jsonb.fromJson(responseBodyJson, JsonObject.class);
                    // Send a Faces info message that add was successful
                    Messages.addGlobalInfo("Successfully added data with name {0}", responseJsonObject.getString("name"));
                    // Reset the selected instance to null
                    selectedFirebaseWeatherForecast = null;

                } else {
                    // Send a Faces info message that add was not successful
                    Messages.addGlobalInfo("Add was not successful, server return status {0}", httpResponse.statusCode());
                }

            } else {

                // Build the url path to object to update
//                String _jsonSingleDataPath = String.format("%s/%s/%s.json",
//                        FIREBASE_REALTIME_DATABASE_BASE_URL, FirebaseWeatherForecast.class.getSimpleName(), selectedFirebaseWeatherForecast.getName());
                String _jsonSingleDataPath = String.format("%s/%sOwner/%s/%s.json?auth=%s",
                        FIREBASE_REALTIME_DATABASE_BASE_URL,
                        FirebaseWeatherForecast.class.getSimpleName(),
                        firebaseUserId,
                        selectedFirebaseWeatherForecast.getName(),
                        firebaseToken);

                // Create and Http Request to send an HTTP PUT request to write over existing data
                var httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(_jsonSingleDataPath))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                        .build();
                // Send the Http Request
                var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                // Check if the Http Response was successful
                if (httpResponse.statusCode() == 200) {
                    // Get the body of the Http Response
                    var responseBodyJson = httpResponse.body();
                    // Convert the JSON String to an FirebaseWeatherForecast instance
                    FirebaseWeatherForecast updatedFirebaseWeatherForecast = jsonb.fromJson(responseBodyJson, FirebaseWeatherForecast.class);
                    // Send a Faces info message that update was successful
                    Messages.addGlobalInfo("Successfully updated FirebaseWeatherForecast {0}",
                            updatedFirebaseWeatherForecast.toString());
                } else {
                    // Send a Faces info message that update was not successful
                    Messages.addGlobalInfo("Update was not successful, server return status {0}", httpResponse.statusCode());
                }

            }

            // Fetch a list of objects from the Firebase RTDB
            fetchFirebaseData();

            // Hide the PrimeFaces dialog
            PrimeFaces.current().executeScript("PF('manageFirebaseWeatherForecastDialog').hide()");

        } catch (Exception e) {
            // Send a Faces info message that an error occurred when saving
            Messages.addGlobalError("Error saving data {0}", e.getMessage());
        }


    }

    /**
     * Remove currentFirebaseWeatherForecast to Firebase Realtime Database using the REST API
     *
     * @link <a href="https://firebase.google.com/docs/reference/rest/database">Firebase Realtime Database REST API</a>
     */
    public void onDelete() {
        // Get the Firebase Authenticated userId and token.
        String firebaseUserId = _firebaseLoginSession.getFirebaseUser().getLocalId();
        String firebaseToken = _firebaseLoginSession.getFirebaseUser().getIdToken();

        // Jsonb is used for converting Java objects to a JSON string or visa-versa
        // HttpClient is native Java library for sending Http Request to a web server
        try (Jsonb jsonb = JsonbBuilder.create();
             var httpClient = HttpClient.newHttpClient()) {

            // Get the unique name of the Json object to delete
            String name = selectedFirebaseWeatherForecast.getName();
            // Build the URL path of the Json object to delete
//            String _jsonSingleDataPath = String.format("%s/%s/%s.json",
//                    FIREBASE_REALTIME_DATABASE_BASE_URL, FirebaseWeatherForecast.class.getSimpleName(), name);
            String _jsonSingleDataPath = String.format("%s/%sOwner/%s/%s.json?auth=%s",
                    FIREBASE_REALTIME_DATABASE_BASE_URL,
                    FirebaseWeatherForecast.class.getSimpleName(),
                    firebaseUserId,
                    selectedFirebaseWeatherForecast.getName(),
                    firebaseToken);

            // Create an DELETE Http Request
            var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(_jsonSingleDataPath))
                    .DELETE()
                    .build();
            // Send the DELETE Http Request
            var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            // Check if the Http Response was successful
            if (httpResponse.statusCode() == 200) {
                Messages.addGlobalInfo("Successfully deleted data with name {0}", name);
                // Fetch new data from Firebase
                fetchFirebaseData();
            } else {
                // Send a Faces info message that delete was not successful
                Messages.addGlobalInfo("Delete was not successful, server return status {0}", httpResponse.statusCode());
            }

        } catch (Exception e) {
            // Send a Faces error message with the exception message
            Messages.addGlobalError("Error deleting Firebase Realtime Database data {0}", e.getMessage());
        }

    }

    /**
     * Get currentFirebaseWeatherForecast to Firebase Realtime Database using the REST API
     *
     * @link <a href="https://firebase.google.com/docs/reference/rest/database">Firebase Realtime Database REST API</a>
     */
    private void fetchFirebaseData() {
        // Jsonb is used for converting Java objects to a JSON string or visa-versa
        // HttpClient is native Java library for sending Http Request to a web server
        try (Jsonb jsonb = JsonbBuilder.create();
             var httpClient = HttpClient.newHttpClient()) {

            // Create an GET Http Request to fetch all data
            var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(_jsonAllDataPath))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            // Send the GET Http Request
            var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            // Check if the Http Request was successful
            if (httpResponse.statusCode() == 200) {
                // Get the body of the Http Response
                var responseBodyJson = httpResponse.body();
                // Convert the responseBodyJson to an LinkedHashMap<String, FirebaseWeatherForecast>
                LinkedHashMap<String, FirebaseWeatherForecast> responseData = jsonb.fromJson(responseBodyJson, new LinkedHashMap<String, FirebaseWeatherForecast>() {
                }.getClass().getGenericSuperclass());
                // Convert the LinkedHashMap<String, FirebaseWeatherForecast> to List<FirebaseWeatherForecast>
                firebaseWeatherForecasts = responseData.entrySet()
                        .stream()
                        .map(item -> {
                            var currentFirebaseWeatherForecast= new FirebaseWeatherForecast();
                            currentFirebaseWeatherForecast.setName(item.getKey());

                            currentFirebaseWeatherForecast.setCity(item.getValue().getCity());
                            currentFirebaseWeatherForecast.setDate(item.getValue().getDate());
                            currentFirebaseWeatherForecast.setDescription(item.getValue().getDescription());
                            currentFirebaseWeatherForecast.setTemperatureCelsius(item.getValue().getTemperatureCelsius());

                            return currentFirebaseWeatherForecast;
                        })
                        .toList();

                Messages.addGlobalInfo("Successfully fetched Firebase Realtime Database data");
                PrimeFaces.current().ajax().update("dialogs:messages", "form:dt-FirebaseWeatherForecasts");
            } else {
                Messages.addGlobalInfo("Fetch data was not successful, server return status {0}", httpResponse.statusCode());
            }

        } catch (Exception e) {
            Messages.addGlobalError("Error adding Firebase Realtime Database data {0}", e.getMessage());
        }
    }
}