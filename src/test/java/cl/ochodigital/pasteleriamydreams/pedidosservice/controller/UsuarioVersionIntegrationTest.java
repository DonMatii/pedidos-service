package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Versión habilitada por usuario (demo de las tres versiones): la semilla
// asigna cada correo de prueba de Google a su capa, y un correo sin versión
// queda fuera (además de la puerta de usuarios de prueba de Google).
@SpringBootTest
@AutoConfigureMockMvc
class UsuarioVersionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cadaUsuarioDePruebaTieneSuVersionHabilitada() throws Exception {
        mockMvc.perform(get("/api/usuarios/version").param("email", "catherine.godoy.g@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/usuarios/version").param("email", "mat.suazog@duocuc.cl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/usuarios/version").param("email", "contacto.donmatii@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void elCorreoSinVersionQuedaFuera() throws Exception {
        mockMvc.perform(get("/api/usuarios/version").param("email", "desconocido@ejemplo.cl"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void elEmailEsObligatorio() throws Exception {
        mockMvc.perform(get("/api/usuarios/version").param("email", " "))
                .andExpect(status().isBadRequest());
    }
}
