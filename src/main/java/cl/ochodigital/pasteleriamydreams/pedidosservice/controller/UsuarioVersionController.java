package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.UsuarioVersion;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.UsuarioVersionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Versión habilitada por usuario (demo de las tres versiones). El front la
// consulta al hacer login para encender o apagar las funciones de cada capa.
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioVersionController {

    private final UsuarioVersionRepository repository;

    public UsuarioVersionController(UsuarioVersionRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/version")
    public ResponseEntity<?> versionDeUsuario(@RequestParam("email") String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "El email es obligatorio"));
        }
        return repository.findById(email.trim().toLowerCase())
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(
                        Map.of("email", u.getEmail(), "version", u.getVersion())))
                .orElseGet(() -> ResponseEntity.status(403)
                        .body(Map.of("mensaje", "Usuario sin versión habilitada")));
    }
}
