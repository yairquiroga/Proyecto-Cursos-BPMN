package ar.edu.unrc.cursos.webapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Seguridad: autentica a los usuarios contra la API IDM de Flowable.
 * Así los 5 usuarios del personal (jefe_admin, admin_2, jefe_docente, etc.)
 * acceden con sus credenciales de Flowable, sin cuentas separadas en la BD.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${flowable.rest.base-url}")
    private String flowableBaseUrl;

    @Value("${flowable.rest.user}")
    private String adminUser;

    @Value("${flowable.rest.password}")
    private String adminPass;

    // Grupos conocidos → roles de Spring Security
    private static final Map<String, String> GRUPO_ROL = Map.of(
            "Administracion", "ROLE_ADMIN",
            "Docentes",       "ROLE_DOCENTE"
    );

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                // Formulario público de solicitud: acceso libre
                .antMatchers("/", "/solicitud", "/solicitud/nueva", "/css/**").permitAll()
                // Admin
                .antMatchers("/admin/**").hasRole("ADMIN")
                // Jefe docente
                .antMatchers("/docente/**").hasRole("DOCENTE")
                .anyRequest().authenticated()
            .and()
            .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/portal", true)
                .permitAll()
            .and()
            .logout()
                .logoutSuccessUrl("/login?logout")
                .permitAll();
        return http.build();
    }

    @Bean
    public AuthenticationProvider flowableAuthProvider() {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication auth) {
                String username = auth.getName();
                String password = auth.getCredentials().toString();

                // Verificar credenciales contra Flowable IDM
                try {
                    String credentials = Base64.getEncoder()
                            .encodeToString((username + ":" + password).getBytes());
                    WebClient client = WebClient.builder()
                            .baseUrl(flowableBaseUrl)
                            .defaultHeader("Authorization", "Basic " + credentials)
                            .build();

                    // Si el call no falla (200 OK) → credenciales válidas
                    Map<?,?> user = client.get()
                            .uri("/idm-api/users/" + username)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

                    if (user == null || !username.equals(user.get("id"))) {
                        throw new BadCredentialsException("Usuario no encontrado");
                    }

                    // Obtener grupos del usuario para asignar roles
                    List<Map<?,?>> groups = client.get()
                            .uri("/idm-api/users/" + username + "/groups")
                            .retrieve()
                            .bodyToFlux(Map.class)
                            .collectList()
                            .block();

                    List<GrantedAuthority> authorities = new ArrayList<>();
                    if (groups != null) {
                        for (Map<?,?> g : groups) {
                            String gid = (String) g.get("id");
                            String rol = GRUPO_ROL.get(gid);
                            if (rol != null) authorities.add(new SimpleGrantedAuthority(rol));
                        }
                    }
                    // admin de Flowable también puede usar el portal como admin
                    if ("admin".equals(username)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }

                    return new UsernamePasswordAuthenticationToken(username, password, authorities);

                } catch (Exception e) {
                    throw new BadCredentialsException("Credenciales inválidas: " + e.getMessage());
                }
            }

            @Override
            public boolean supports(Class<?> cls) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(cls);
            }
        };
    }
}
