package cl.ochodigital.pasteleriamydreams.pedidosservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// Protege los endpoints administrativos de /api/pedidos con una API key (header X-Api-Key).
// Publico sin key: POST /api/pedidos (crear pedido) y GET /api/pedidos/seguimiento/{codigo}.
// El valor viene de la variable de entorno app.admin-api-key / APP_ADMIN_API_KEY (RNF-11:
// la config vive en el entorno, nunca en el codigo). Si la key no esta configurada,
// los endpoints protegidos responden 503 (fail-closed) en vez de quedar abiertos.
@Component
public class AdminApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyFilter.class);

    private static final String RUTA_API = "/api/pedidos";

    private final String apiKeyConfigurada;

    public AdminApiKeyFilter(@Value("${app.admin-api-key:}") String apiKeyConfigurada) {
        this.apiKeyConfigurada = apiKeyConfigurada;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String metodo = request.getMethod();
        String ruta = request.getRequestURI();

        // El preflight de CORS nunca lleva headers: se deja pasar
        if ("OPTIONS".equalsIgnoreCase(metodo) || !ruta.startsWith(RUTA_API)) {
            chain.doFilter(request, response);
            return;
        }

        boolean esPublico = ("POST".equalsIgnoreCase(metodo) && ruta.equals(RUTA_API))
                || ("GET".equalsIgnoreCase(metodo) && ruta.startsWith(RUTA_API + "/seguimiento/"));
        if (esPublico) {
            chain.doFilter(request, response);
            return;
        }

        if (apiKeyConfigurada == null || apiKeyConfigurada.isBlank()) {
            log.error("app.admin-api-key NO configurada: se bloquean los endpoints administrativos de pedidos (503)");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"mensaje\":\"Seguridad no configurada en el servidor\"}");
            return;
        }

        String apiKeyRecibida = request.getHeader("X-Api-Key");
        boolean coincide = apiKeyRecibida != null
                && MessageDigest.isEqual(apiKeyConfigurada.getBytes(StandardCharsets.UTF_8),
                                          apiKeyRecibida.getBytes(StandardCharsets.UTF_8));
        if (!coincide) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"mensaje\":\"API key invalida\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
