package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.UsuarioVersion;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.UsuarioVersionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Siembra inicial de las versiones por usuario (solo si la tabla está vacía):
// los correos son los usuarios de prueba de la consola de Google Auth Platform.
// Cambiar la asignación = un UPDATE sobre usuarios_version, no tocar código.
@Component
public class UsuarioVersionSeed {

    private static final Logger log = LoggerFactory.getLogger(UsuarioVersionSeed.class);

    private final UsuarioVersionRepository repository;

    public UsuarioVersionSeed(UsuarioVersionRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void sembrar() {
        if (repository.count() > 0) {
            return;
        }
        sembrar("catherine.godoy.g@gmail.com", 1);
        sembrar("cat.godoyg@duocuc.cl", 1);
        sembrar("mat.suazog@duocuc.cl", 2);
        sembrar("contacto.donmatii@gmail.com", 3);
        sembrar("jav.penar@profesor.duoc.cl", 3);
        log.info("Usuarios de versión sembrados (asignación editable en la tabla usuarios_version)");
    }

    private void sembrar(String email, int version) {
        UsuarioVersion usuario = new UsuarioVersion();
        usuario.setEmail(email);
        usuario.setVersion(version);
        repository.save(usuario);
    }
}
