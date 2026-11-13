package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.UsuarioVersion;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.UsuarioVersionRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// Versión habilitada por usuario para la capa de seguridad (S7). Traduce el email
// confiable del JWT de Google en la versión de la tabla usuarios_version y en las
// autoridades VERSION_1..VERSION_N que Spring Security exige en los endpoints de
// usuario. La consulta normaliza el email igual que UsuarioVersionController.
@Service
public class UsuarioVersionService {

    private final UsuarioVersionRepository repository;

    public UsuarioVersionService(UsuarioVersionRepository repository) {
        this.repository = repository;
    }

    // Versión habilitada del usuario; 0 si el email no existe o no tiene versión
    public int versionDe(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        return repository.findById(email.trim().toLowerCase())
                .map(UsuarioVersion::getVersion)
                .orElse(0);
    }

    // Autoridades VERSION_1..VERSION_N: una versión N habilita todos los niveles
    // hasta N (un usuario V3 puede usar también las funciones de V1 y V2). Sin
    // versión habilitada no hay autoridades y los endpoints de usuario responden 403.
    public List<GrantedAuthority> autoridadesDe(String email) {
        int version = versionDe(email);
        List<GrantedAuthority> autoridades = new ArrayList<>();
        for (int nivel = 1; nivel <= version; nivel++) {
            autoridades.add(new SimpleGrantedAuthority("VERSION_" + nivel));
        }
        return autoridades;
    }
}
