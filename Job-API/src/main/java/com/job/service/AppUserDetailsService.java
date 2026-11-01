package com.job.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.job.repo.AppUserRepo;

@Service
public class AppUserDetailsService implements UserDetailsService {
	private final AppUserRepo userRepo;

	public AppUserDetailsService(AppUserRepo userRepo) {
		this.userRepo = userRepo;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return userRepo.findByEmailIgnoreCase(email.trim())
				.map(user -> User.withUsername(user.getId())
						.password(user.getPasswordHash())
						.roles("USER")
						.build())
				.orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
	}
}
