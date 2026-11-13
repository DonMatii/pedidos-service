package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import cl.ochodigital.pasteleriamydreams.pedidosservice.client.DeliciaClient;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.SolicitudRepository;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.UsuarioVersion;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.UsuarioVersionRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Aceptación y rechazo de los endpoints de usuario según el JWT de Google (S7).
// Los tokens se firman DE VERDAD con la clave privada de pruebas cuya clave
// pública está en src/test/resources/application.properties
// (app.seguridad.jwt.public-key-pem): la validación criptográfica es real y la
// suite nunca habla con el JWKS de Google. El email del cuerpo de la petición
// es solo un dato de CONTACTO: la versión se exige por el email del token.
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionJwtIntegrationTest {

    // Misma audience que define src/test/resources/application.properties
    private static final String AUDIENCIA = "client-id-de-prueba";

    private static final String EMISOR_GOOGLE = "https://accounts.google.com";

    // Usuarios sembrados por esta misma clase: V1, V2 y V3
    private static final String USUARIO_V1 = "v1.jwt@prueba.cl";
    private static final String USUARIO_V2 = "v2.jwt@prueba.cl";
    private static final String USUARIO_V3 = "v3.jwt@prueba.cl";
    private static final String USUARIO_SIN_VERSION = "sin.version@prueba.cl";

    // Clave RSA SOLO de pruebas (generada para la suite, sin valor en producción).
    // Es la pareja de app.seguridad.jwt.public-key-pem de los properties de test:
    // con ella se firman tokens reales que el decoder offline debe aceptar/rechazar.
    private static final String CLAVE_PRIVADA_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIIEwAIBADANBgkqhkiG9w0BAQEFAASCBKowggSmAgEAAoIBAQDSG0D2+r/pzCwh
            ugeCvvX/Aafgmvr0MBNz6UBhyUmCJcT8/zqCicwfM1ihliamqIkSMDfX3x75sjmQ
            HaSLXUcH0c9FKiYyt+RHBewg7eRrCn0Xe06ocOqCkfBFSzyVv7H1bmbz0r9D+eYV
            CwbhX2FlBo9bCK4XGq1k5GuHIIo7WZTHuNQ6E/IsYDlipNWPCTYXN8zdOAWjkX5N
            T4FX4h03O+58ZJe3pcEhkrvFvUnjwW0LoiohnKV05jMbxa14LC2DJ9ynjfL7Gzsi
            uCv/2E/wevQe1j26a+sD1KafyK1OGrC166AsiLZrMGLeQltqkgl6x+quY70b2e3w
            psbauAKNAgMBAAECggEBAMEUbpUJRcfS61NrI2/RkTrxCJdtlOVGMPo6BgJW+ghy
            MR7WZayEeWLKys5jr81D4AMo8Iie8FZCpId91jKEW7wh4ToMS+Ib/9oM5Tw1oc2E
            bARiOpysd7XZ61WimGB9M0r6U/4JU99iDUCVS5NdFaHDb4YW6fUtfN7U0sPw8Vcr
            hyojr02I2081Mo5LTigjq4sPK2uQacViOhdEjN3d0v627XX+2+iESHk6fcrlSh6v
            qHXQ6/eE0pR6CTeMnRMAq96jERmL1MKevaKV47hu1Ozfu+JlvcnUc+xiTEzXEj0M
            9f3UoYeQSbbwv9Sm43bjSmVhlzC/VuOHds7S6IRJnLkCgYEA7F3W6KHpNtDUw/Dh
            bWwblPlDcL2TRsAumCw0ZdVCeLJ5eIj+A9Suf9xvd6NgU3cq3nRnPN1SiwNT+oP9
            fyYMB4xenId1rLG0Vv6pjjwYTv17tq8/+oQvjmqJz74rQg0IfguL54gWC07ME63S
            bS0uVi6wOGumO9PTK5OkzqIdJyMCgYEA448DOxXBkSpR7ELJIFHZA9kf+DRe1W0S
            1fhw/uJyjEvK6zbFsNYRi5lfhxFSaHyDE1ujfEHHOaV8bvpA3OfGdtkylTZfcA9s
            0vIQGkpdriXOe1MrLeJGm1oXt9Rl5puSKeoQJtXmnMOTn9XPaIpmTgkGmQcB/j0K
            jKRtrO/Zoo8CgYEAjcajt5oWlwif9n+v18B2st7osuEjS9bOm/w32bUXT+pNrh0N
            sCKMIKmKOsrGZooeLpzHp9xhPHzcjHFLRk79/DEYNu+Rh+p3b48FCQnG0up8oFFy
            IMPU+qmwy+kXbWD/7Qrj+hbIkjPLeSpUA6SMHKmYjd8UMOKX0ZWXz1GyBt0CgYEA
            jD8M57Jd7SDfArSajo/DJ0r/GBL2y6UAsdz/jydzH6JqupK0PmuAvr/VD0KOvpTW
            l+ynkqJbrdyfFyqnJT63o3HzaeDr6v2mvU+wuvNQT08/teQpPYsypvZcndCKyRji
            eXCnabf0/HLRHfZBcRWzPa70YQXfpZhrUwjYC3h7cmMCgYEAkCi1RMRhZMsdWLZ3
            Bl2B5lklzT/TG9LSwSoS3YS2ouj0YLXHSvPsnhIiC6p15ILcR9K00m9GAdVZ4/2K
            07WwKQUZXgDwmdDDo1nQIGtJHex+FkzOS3IOjQvMtvxkMJnZlC5Eu2yWgxEsKxHC
            yH1F0+4BInO/CdQ1oTnpr4k4uEQ=
            -----END PRIVATE KEY-----
            """;

    // Cuerpos mínimos válidos: el email del cuerpo es el de CONTACTO, no el del token
    private static final String PEDIDO_JSON =
            "{\"cliente\":\"Cliente Token\",\"email\":\"contacto@ejemplo.cl\","
                    + "\"productos\":[{\"nombre\":\"Torta\",\"cantidad\":1,\"precioUnitario\":10000}]}";

    private static final String SOLICITUD_JSON =
            "{\"nombre\":\"Cliente Token\",\"email\":\"contacto@ejemplo.cl\",\"detalle\":\"Torta x1\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private SolicitudRepository solicitudRepository;

    @Autowired
    private UsuarioVersionRepository usuarioVersionRepository;

    // El cliente de catálogo se mockea: la suite no depende de catalogo-service
    @MockBean
    private DeliciaClient deliciaClient;

    @BeforeEach
    void preparar() {
        pedidoRepository.deleteAll();
        solicitudRepository.deleteAll();
        // Usuarios propios de esta clase: la siembra general no se toca
        guardarUsuario(USUARIO_V1, 1);
        guardarUsuario(USUARIO_V2, 2);
        guardarUsuario(USUARIO_V3, 3);
        if (usuarioVersionRepository.existsById(USUARIO_SIN_VERSION)) {
            usuarioVersionRepository.deleteById(USUARIO_SIN_VERSION);
        }
    }

    // (a) Sin token los endpoints de usuario no aceptan nada: 401 con el mensaje fijo
    @Test
    void sinTokenLosEndpointsDeUsuarioPidenSesionConGoogle() throws Exception {
        long pedidosAntes = pedidoRepository.count();
        long solicitudesAntes = solicitudRepository.count();

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere sesión con Google"));

        mockMvc.perform(post("/api/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOLICITUD_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere sesión con Google"));

        // Nada quedó guardado
        assertEquals(pedidosAntes, pedidoRepository.count());
        assertEquals(solicitudesAntes, solicitudRepository.count());
    }

    // (b) Un token firmado con otra clave no pasa la validación criptográfica real
    @Test
    void tokenConFirmaInvalidaSeRechaza() throws Exception {
        long pedidosAntes = pedidoRepository.count();

        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        RSAPrivateKey claveAjena = (RSAPrivateKey) generador.generateKeyPair().getPrivate();
        String token = firmarToken(EMISOR_GOOGLE, AUDIENCIA, USUARIO_V2,
                Instant.now().plusSeconds(3600), claveAjena);

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere sesión con Google"));

        assertEquals(pedidosAntes, pedidoRepository.count());
    }

    // (c) La audiencia del token debe ser el client id configurado
    @Test
    void tokenConAudienceIncorrectaSeRechaza() throws Exception {
        long pedidosAntes = pedidoRepository.count();

        String token = firmarToken(EMISOR_GOOGLE, "otro-cliente", USUARIO_V2,
                Instant.now().plusSeconds(3600), clavePrivadaDePrueba());

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere sesión con Google"));

        assertEquals(pedidosAntes, pedidoRepository.count());
    }

    // (d) Un token expirado no se acepta aunque la firma sea válida
    @Test
    void tokenExpiradoSeRechaza() throws Exception {
        long pedidosAntes = pedidoRepository.count();

        String token = firmarToken(EMISOR_GOOGLE, AUDIENCIA, USUARIO_V2,
                Instant.now().minusSeconds(3600), clavePrivadaDePrueba());

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere sesión con Google"));

        assertEquals(pedidosAntes, pedidoRepository.count());
    }

    // (e) Un usuario V1 con token válido NO puede crear pedidos: la versión 2 es
    // la que habilita la compra. El email del cuerpo no cambia la decisión: la
    // versión se lee SOLO del token.
    @Test
    void usuarioV1ConTokenValidoNoPuedeCrearPedidos() throws Exception {
        long pedidosAntes = pedidoRepository.count();

        String token = firmarToken(EMISOR_GOOGLE, AUDIENCIA, USUARIO_V1,
                Instant.now().plusSeconds(3600), clavePrivadaDePrueba());

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Tu versión no incluye esta función"));

        // Aunque el email del cuerpo sea el de un usuario V2, la versión manda la del token
        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"Cliente Contacto\",\"email\":\"" + USUARIO_V2 + "\","
                                + "\"productos\":[{\"nombre\":\"Torta\",\"cantidad\":1,\"precioUnitario\":10000}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Tu versión no incluye esta función"));

        assertEquals(pedidosAntes, pedidoRepository.count());
    }

    // (f) Un usuario V1 con token válido SÍ puede crear solicitudes (función de la V1)
    @Test
    void usuarioV1ConTokenValidoCreaSolicitud() throws Exception {
        long solicitudesAntes = solicitudRepository.count();

        String token = firmarToken(EMISOR_GOOGLE, AUDIENCIA, USUARIO_V1,
                Instant.now().plusSeconds(3600), clavePrivadaDePrueba());

        mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOLICITUD_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Cliente Token"));

        assertEquals(solicitudesAntes + 1, solicitudRepository.count());
    }

    // (g) Un email que no está en usuarios_version no tiene ninguna función habilitada
    @Test
    void usuarioDesconocidoConTokenValidoNoCreaNada() throws Exception {
        long pedidosAntes = pedidoRepository.count();
        long solicitudesAntes = solicitudRepository.count();

        String token = firmarToken(EMISOR_GOOGLE, AUDIENCIA, USUARIO_SIN_VERSION,
                Instant.now().plusSeconds(3600), clavePrivadaDePrueba());

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Usuario sin versión habilitada"));

        mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOLICITUD_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Usuario sin versión habilitada"));

        // Nada quedó guardado
        assertEquals(pedidosAntes, pedidoRepository.count());
        assertEquals(solicitudesAntes, solicitudRepository.count());
    }

    // Regla de niveles: exigir "versión >= 2" también acepta a un usuario V3
    @Test
    void usuarioV2ConTokenValidoCreaPedidos() throws Exception {
        long pedidosAntes = pedidoRepository.count();

        String token = firmarToken(EMISOR_GOOGLE, AUDIENCIA, USUARIO_V2,
                Instant.now().plusSeconds(3600), clavePrivadaDePrueba());

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoConsulta").isNotEmpty());

        assertEquals(pedidosAntes + 1, pedidoRepository.count());
    }

    // Regla de niveles: una versión N habilita también las funciones de las
    // versiones anteriores (V3 puede comprar y también pedir solicitudes)
    @Test
    void usuarioV3UsaLasFuncionesDeLasVersionesAnteriores() throws Exception {
        String token = firmarToken(EMISOR_GOOGLE, AUDIENCIA, USUARIO_V3,
                Instant.now().plusSeconds(3600), clavePrivadaDePrueba());

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOLICITUD_JSON))
                .andExpect(status().isCreated());
    }

    // (h) Regresión: el flujo administrativo con X-Api-Key sigue respondiendo igual
    @Test
    void flujoAdminConApiKeySigueFuncionando() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setCliente("Daniela Soto");
        pedido.setEmail("daniela@ejemplo.cl");
        pedido.setTotal(18990);
        Long idPedido = pedidoRepository.save(pedido).getId();

        mockMvc.perform(patch("/api/pedidos/{id}/estado", idPedido)
                        .header("X-Api-Key", "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));

        mockMvc.perform(get("/api/pedidos")
                        .header("X-Api-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Sin key (o con key incorrecta) responde 401 con el mismo mensaje de siempre
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("API key invalida"));

        mockMvc.perform(get("/api/pedidos")
                        .header("X-Api-Key", "clave-equivocada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("API key invalida"));
    }

    // (h) Regresión: los GET públicos siguen públicos, sin token y sin API key
    @Test
    void losGetPublicosSiguenPublicos() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setCliente("Catherine Godoy");
        pedido.setEmail("catherine@ejemplo.cl");
        pedido.setTotal(120000);
        pedido.setCodigoConsulta("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4");
        pedidoRepository.save(pedido);

        mockMvc.perform(get("/api/pedidos/seguimiento/{codigo}", "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoConsulta").value("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"));

        mockMvc.perform(get("/api/pedidos/bienvenida/estado").param("email", "nunca-canjeo@ejemplo.cl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elegible").value(true));

        mockMvc.perform(get("/api/usuarios/version").param("email", USUARIO_V1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // El detalle por id sigue siendo administrativo: sin API key no entra
        mockMvc.perform(get("/api/pedidos/{id}", pedido.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("API key invalida"));
    }

    private void guardarUsuario(String email, int version) {
        UsuarioVersion usuario = new UsuarioVersion();
        usuario.setEmail(email);
        usuario.setVersion(version);
        usuarioVersionRepository.save(usuario);
    }

    // Firma un JWT con la clave indicada (RS256), con los claims que Google manda
    // (incluido `azp`: un ID token real de Google también lo trae, y no debe
    // bastar para pasar la audiencia si `aud` no corresponde)
    private String firmarToken(String emisor, String audiencia, String email,
                               Instant expiracion, PrivateKey clave) throws JOSEException, ParseException {
        JWTClaimsSet reclamos = new JWTClaimsSet.Builder()
                .issuer(emisor)
                .audience(audiencia)
                .subject("google-" + email)
                .claim("email", email)
                .claim("azp", AUDIENCIA)
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(expiracion))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), reclamos);
        token.sign(new RSASSASigner((RSAPrivateKey) clave));
        return token.serialize();
    }

    // Lee la clave privada de pruebas desde su PEM (PKCS#8)
    private static RSAPrivateKey clavePrivadaDePrueba() {
        try {
            String base64 = CLAVE_PRIVADA_PEM
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer la clave privada de pruebas", e);
        }
    }
}
