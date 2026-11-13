package cl.ochodigital.pasteleriamydreams.pedidosservice.config;

import cl.ochodigital.pasteleriamydreams.pedidosservice.service.UsuarioVersionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;

// Configuración de seguridad del backend (S7): el JWT de Google se valida de verdad
// (firma, emisor y audiencia) y de ahí sale el email confiable con el que se exige
// la versión habilitada en los endpoints de usuario:
//   POST /api/pedidos     -> autoridad VERSION_2 (la compra es de la V2)
//   POST /api/solicitudes -> autoridad VERSION_1 (el formulario es de la V1)
// Todo lo demás queda público (los GET siguen públicos) y el flujo administrativo
// lo sigue protegiendo AdminApiKeyFilter con X-Api-Key, sin cambios.
@Configuration
public class SeguridadJwtConfig {

    // Emisores aceptados del JWT de Google (con y sin esquema, según el token)
    private static final String EMISOR_GOOGLE = "https://accounts.google.com";
    private static final String EMISOR_GOOGLE_CORTO = "accounts.google.com";

    private final JwtAutoridadesConverter jwtAutoridadesConverter;
    private final UsuarioVersionService usuarioVersionService;

    public SeguridadJwtConfig(JwtAutoridadesConverter jwtAutoridadesConverter,
                              UsuarioVersionService usuarioVersionService) {
        this.jwtAutoridadesConverter = jwtAutoridadesConverter;
        this.usuarioVersionService = usuarioVersionService;
    }

    // Decoder del JWT de Google. En producción valida contra el JWKS de Google
    // (app.seguridad.jwt.jwk-set-uri, por defecto el de Google). Para los tests se
    // puede fijar app.seguridad.jwt.public-key-pem: el decoder pasa a validar contra
    // esa clave pública local y la suite puede firmar tokens reales sin salir a la red.
    @Bean
    public JwtDecoder decodificadorJwt(
            @Value("${app.seguridad.jwt.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwkSetUri,
            @Value("${app.seguridad.jwt.public-key-pem:}") String clavePublicaPem,
            @Value("${app.seguridad.jwt.audience:}") String audiencia) {

        String pem = clavePublicaPem == null ? "" : clavePublicaPem.trim();
        NimbusJwtDecoder decodificador = pem.isEmpty()
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : NimbusJwtDecoder.withPublicKey(leerClavePublica(pem)).build();

        // Los mismos validadores en ambos caminos: vencimiento, emisor de Google y
        // audiencia (el client id configurado)
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                validadorDeEmisor(),
                new ValidadorAudienciaJwt(audiencia)));
        return decodificador;
    }

    // Cadena de filtros stateless: solo los dos endpoints de usuario exigen sesión de
    // Google con la versión habilitada; el resto queda exactamente como estaba.
    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAuthority("VERSION_2")
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes").hasAuthority("VERSION_1")
                        .anyRequest().permitAll())
                .oauth2ResourceServer(recurso -> recurso
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(this.jwtAutoridadesConverter))
                        .authenticationEntryPoint(puntoDeEntrada()))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(puntoDeEntrada())
                        .accessDeniedHandler(manejadorAccesoDenegado()))
                // Las respuestas de los flujos admin y públicos deben quedar byte a
                // byte como estaban: por eso no se agregan las cabeceras por defecto
                // de Spring Security (la API es JSON autenticado por token, no HTML)
                // ni su procesador CORS (el CORS de los controladores @CrossOrigin
                // sigue funcionando igual que antes de esta feature)
                .cors(cors -> cors.disable())
                .headers(cabeceras -> cabeceras.disable());
        return http.build();
    }

    // 401: no hay sesión de Google válida (falta el token o no supera la validación)
    private AuthenticationEntryPoint puntoDeEntrada() {
        return (request, response, excepcion) ->
                escribirError(response, HttpServletResponse.SC_UNAUTHORIZED, "Se requiere sesión con Google");
    }

    // 403: la sesión es válida pero la versión del usuario no alcanza para el endpoint
    private AccessDeniedHandler manejadorAccesoDenegado() {
        return (request, response, excepcion) -> {
            String email = emailDeLaSesion();
            if (usuarioVersionService.versionDe(email) < 1) {
                escribirError(response, HttpServletResponse.SC_FORBIDDEN, "Usuario sin versión habilitada");
            } else {
                escribirError(response, HttpServletResponse.SC_FORBIDDEN, "Tu versión no incluye esta función");
            }
        };
    }

    // Email confiable de la sesión: viene SOLO del JWT ya validado, nunca del cuerpo
    private String emailDeLaSesion() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("email");
        }
        return null;
    }

    // Cuerpo JSON de error con el mismo shape { "mensaje": ... } que usa el resto
    // del backend (por ejemplo el AdminApiKeyFilter)
    private static void escribirError(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"mensaje\":\"" + mensaje + "\"}");
    }

    // Acepta el emisor de Google con y sin esquema; cualquier otro emisor se rechaza
    private static OAuth2TokenValidator<Jwt> validadorDeEmisor() {
        return jwt -> {
            String emisor = jwt.getClaimAsString("iss");
            if (EMISOR_GOOGLE.equals(emisor) || EMISOR_GOOGLE_CORTO.equals(emisor)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "El emisor del JWT no es Google: " + emisor, null));
        };
    }

    // Lee una clave pública RSA en PEM (PKCS#8/SPKI) para el decoder offline de tests
    private static RSAPublicKey leerClavePublica(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("app.seguridad.jwt.public-key-pem no es una clave pública RSA válida", e);
        }
    }

    // La audiencia del JWT debe ser el client id de Google configurado
    // (app.seguridad.jwt.audience): el claim `aud` (texto o lista) debe contenerlo,
    // tal como manda el contrato congelado. Sin audiencia configurada no se
    // acepta ningún token (fail-closed).
    private static final class ValidadorAudienciaJwt implements OAuth2TokenValidator<Jwt> {

        private final String audiencia;

        private ValidadorAudienciaJwt(String audiencia) {
            this.audiencia = audiencia;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            if (audiencia == null || audiencia.isBlank()) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "app.seguridad.jwt.audience no está configurada en el servidor", null));
            }
            if (coincide(jwt.getClaim("aud"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "La audiencia del JWT no corresponde al client id configurado", null));
        }

        // El claim `aud` llega como texto (ID token de Google) o como lista de textos
        private boolean coincide(Object reclamo) {
            if (reclamo instanceof String valor) {
                return audiencia.equals(valor);
            }
            if (reclamo instanceof Collection<?> valores) {
                return valores.stream().anyMatch(audiencia::equals);
            }
            return false;
        }
    }
}
