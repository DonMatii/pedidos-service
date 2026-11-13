package cl.ochodigital.pasteleriamydreams.pedidosservice.config;

import cl.ochodigital.pasteleriamydreams.pedidosservice.service.UsuarioVersionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

// Convierte el JWT YA VALIDADO (firma, emisor y audiencia) en la autenticación del
// request. El email del claim `email` es el único email confiable: es el que Google
// certificó, y con él se consultan las autoridades VERSION_1..VERSION_N del usuario.
// El email del cuerpo de la petición es solo un dato de contacto y nunca se usa aquí.
@Component
public class JwtAutoridadesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UsuarioVersionService usuarioVersionService;

    public JwtAutoridadesConverter(UsuarioVersionService usuarioVersionService) {
        this.usuarioVersionService = usuarioVersionService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        List<GrantedAuthority> autoridades = usuarioVersionService.autoridadesDe(email);
        return new JwtAuthenticationToken(jwt, autoridades);
    }
}
