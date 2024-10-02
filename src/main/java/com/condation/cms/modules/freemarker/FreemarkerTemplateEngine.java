package com.condation.cms.modules.freemarker;

/*-
 * #%L
 * freemarker-module
 * %%
 * Copyright (C) 2024 CondationCMS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


import com.condation.cms.api.ServerProperties;
import com.condation.cms.api.db.DB;
import com.condation.cms.api.db.DBFileSystem;
import com.condation.cms.api.template.TemplateEngine;
import com.condation.cms.api.theme.Theme;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.NullCacheStorage;
import freemarker.cache.TemplateLoader;
import java.io.IOException;
import java.io.StringWriter;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FreemarkerTemplateEngine implements TemplateEngine {

	private final Configuration config;

	private final DB db;

	public FreemarkerTemplateEngine(final DB db, final ServerProperties serverProperties, final Theme theme) {
		
		this.db = db;
		config = new Configuration(Configuration.VERSION_2_3_32);

		try {
			config.setTemplateLoader(createTemplateLoader(db.getFileSystem(), theme));
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		config.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
		config.setDefaultEncoding("UTF-8");
		config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		config.setLogTemplateExceptions(false);
		config.setWrapUncheckedExceptions(true);
		config.setFallbackOnNullLoopVariable(false);

		if (serverProperties.dev()) {
			config.setCacheStorage(new NullCacheStorage());
			config.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
		} else {
			config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
			config.setLogTemplateExceptions(false);
			config.setWrapUncheckedExceptions(true);
			config.setFallbackOnNullLoopVariable(false);
		}

		config.setSharedVariable("indexOf", new IndexOfMethod());
		config.setSharedVariable("upper", new UpperDirective());
	}
	
	private TemplateLoader createTemplateLoader (final DBFileSystem fileSystem, final Theme theme) throws IOException {
		
		List<TemplateLoader> loaders = new ArrayList<>();
		loaders.add(new FileTemplateLoader(fileSystem.resolve("templates/").toFile()));
		
		if (!theme.empty()) {
			loaders.add(new FileTemplateLoader(theme.templatesPath().toFile()));
			
			if (theme.getParentTheme() != null) {
				loaders.add(new FileTemplateLoader(theme.getParentTheme().templatesPath().toFile()));
			}	
		}
		return new MultiTemplateLoader(
				loaders.toArray(TemplateLoader[]::new)
		);
	}

	@Override
	public String render(final String template, final FreemarkerTemplateEngine.Model model) throws IOException {
		try (StringWriter out = new StringWriter()) {
			Template loadedTemplate = config.getTemplate(template);

			loadedTemplate.process(model.values, out);

			return out.toString();
		} catch (TemplateException | IOException e) {
			throw new IOException(e);
		}
	}

	@Override
	public String renderFromString(String templateString, Model model) throws IOException {
		try (StringWriter out = new StringWriter()) {
			Template loadedTemplate = new Template(UUID.randomUUID().toString(), templateString, config);

			loadedTemplate.process(model.values, out);

			return out.toString();
		} catch (TemplateException | IOException e) {
			throw new IOException(e);
		}
	}
	
	

	@Override
	public void invalidateCache() {
		config.clearTemplateCache();
	}

	@Override
	public void updateTheme(Theme theme) {
		try {
			config.setTemplateLoader(createTemplateLoader(db.getFileSystem(), theme));
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
	
}
