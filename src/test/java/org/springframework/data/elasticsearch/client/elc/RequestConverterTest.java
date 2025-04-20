/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.elasticsearch.client.elc;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.DocValueField;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.lang.Nullable;

/**
 * @author Peter-Josef Meisch
 * @author Han Seungwoo
 * @author Hyuncheol Park
 */
class RequestConverterTest {

	private static final SimpleElasticsearchMappingContext mappingContext = new SimpleElasticsearchMappingContext();
	private static final MappingElasticsearchConverter converter = new MappingElasticsearchConverter(mappingContext);
	private final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
	private final RequestConverter requestConverter = new RequestConverter(converter, jsonpMapper);

	@Test // #2316
	@DisplayName("should add docvalue_fields")
	void shouldAddDocvalueFields() {

		var docValueFields = List.of( //
				new DocValueField("field1"), //
				new DocValueField("field2", "format2") //
		);
		// doesn't matter what type of query is used, the relevant part for docvalue_fields is in the base builder.
		var query = StringQuery.builder("""
				{
					"match_all":{}
				}
				""") //
				.withDocValueFields(docValueFields) //
				.build();

		var searchRequest = requestConverter.searchRequest(query, null, SampleEntity.class, IndexCoordinates.of("foo"),
				true);

		var fieldAndFormats = searchRequest.docvalueFields();
		assertThat(fieldAndFormats).hasSize(2);
		assertThat(fieldAndFormats.get(0).field()).isEqualTo("field1");
		assertThat(fieldAndFormats.get(0).format()).isNull();
		assertThat(fieldAndFormats.get(1).field()).isEqualTo("field2");
		assertThat(fieldAndFormats.get(1).format()).isEqualTo("format2");
	}

	@Test // #2973
	@DisplayName("should set refresh based on deleteRequest")
	void refreshSetByDeleteRequest() {
		var query = new CriteriaQuery(new Criteria("text").contains("test"));
		var deleteQuery = DeleteQuery.builder(query).withRefresh(true).build();

		var deleteByQueryRequest = requestConverter.documentDeleteByQueryRequest(deleteQuery, null, SampleEntity.class,
			IndexCoordinates.of("foo"),
			null);

		assertThat(deleteByQueryRequest.refresh()).isTrue();
	}

	@Test // #3089
	@DisplayName("searchRequest() - When maxResults is set (size < maxResults), pageSize should be the minimum of maxResults and pageable size")
	void searchRequestPageSizeSmallerThanMaxResults() {
		var size = 123;
		var maxResults = size * 12;

		var query = StringQuery.builder("""
					{
						"match_all":{}
					}
					""")
				.withPageable(Pageable.ofSize(size))
				.withMaxResults(maxResults)
				.build();

		var searchRequest = requestConverter.searchRequest(query, null, SampleEntity.class, IndexCoordinates.of("foo"), false, false, null);
		var actualPageSize = searchRequest.size();

		assertThat(actualPageSize).isEqualTo(size);
		assertThat(actualPageSize).isNotEqualTo(maxResults);
	}

	@Test // #3089
	@DisplayName("searchRequest() - When maxResults is set (size == maxResults), pageSize should be equal to maxResults and pageable size")
	void searchRequestPageSizeEqualToMaxResults() {
		var pageSize = 123;
		var maxResults = pageSize;

		var query = StringQuery.builder("""
					{
						"match_all":{}
					}
					""")
				.withPageable(Pageable.ofSize(pageSize))
				.withMaxResults(maxResults)
				.build();

		var searchRequest = requestConverter.searchRequest(query, null, SampleEntity.class, IndexCoordinates.of("foo"), false, false, null);
		var actualPageSize = searchRequest.size();

		assertThat(actualPageSize).isEqualTo(pageSize);
		assertThat(actualPageSize).isEqualTo(maxResults);
	}

	@Test // #3089
	@DisplayName("searchRequest() - When maxResults is set (size > maxResults), pageSize should be the minimum of maxResults and pageable size")
	void searchRequestPageSizeLargerThanMaxResults() {
		var pageSize = 123;
		var maxResults = 99;

		var query = StringQuery.builder("""
					{
						"match_all":{}
					}
					""")
				.withPageable(Pageable.ofSize(pageSize))
				.withMaxResults(maxResults)
				.build();

		var searchRequest = requestConverter.searchRequest(query, null, SampleEntity.class, IndexCoordinates.of("foo"), false, false, null);
		var actualPageSize = searchRequest.size();

		assertThat(actualPageSize).isNotEqualTo(pageSize);
		assertThat(actualPageSize).isEqualTo(maxResults);
	}

	@Test // #3089
	@DisplayName("searchMsearchRequest() - When maxResults is set (size < maxResults), pageSize should be the minimum of maxResults and pageable size")
	void msearchRequestPageSizeSmallerThanMaxResults() {
		var size = 100;
		var maxResults = 150;

		var query = StringQuery.builder("""
					{
						"match_all":{}
					}
					""")
				.withPageable(Pageable.ofSize(size))
				.withMaxResults(maxResults)
				.build();

		var multiSearchQueryParameter = new ElasticsearchTemplate.MultiSearchQueryParameter(query, SampleEntity.class, IndexCoordinates.of("foo"));
		var multiSearchQueryParameters = List.of(multiSearchQueryParameter);

		var msearchRequest = requestConverter.searchMsearchRequest(multiSearchQueryParameters, null);

		var searchBody = msearchRequest.searches().get(0).body();
		var actualPageSize = searchBody.size();

		assertThat(actualPageSize).isEqualTo(size);
		assertThat(actualPageSize).isNotEqualTo(maxResults);
	}

	@Test // #3089
	@DisplayName("searchMsearchRequest() - When maxResults is set (size == maxResults), pageSize should be equal to maxResults and pageable size")
	void msearchRequestPageSizeEqualToMaxResults() {
		var size = 150;
		var maxResults = size;

		var query = StringQuery.builder("""
					{
						"match_all":{}
					}
					""")
				.withPageable(Pageable.ofSize(size))
				.withMaxResults(maxResults)
				.build();

		var multiSearchQueryParameter = new ElasticsearchTemplate.MultiSearchQueryParameter(query, SampleEntity.class, IndexCoordinates.of("foo"));
		var multiSearchQueryParameters = List.of(multiSearchQueryParameter);

		var msearchRequest = requestConverter.searchMsearchRequest(multiSearchQueryParameters, null);

		var searchBody = msearchRequest.searches().get(0).body();
		var actualPageSize = searchBody.size();

		assertThat(actualPageSize).isEqualTo(size);
		assertThat(actualPageSize).isEqualTo(maxResults);
	}

	@Test // #3089
	@DisplayName("searchMsearchRequest() - When maxResults is set (size > maxResults), pageSize should be the minimum of maxResults and pageable size")
	void msearchRequestPageSizeLargerThanMaxResults() {
		var size = 200;
		var maxResults = 150;

		var query = StringQuery.builder("""
					{
						"match_all":{}
					}
					""")
				.withPageable(Pageable.ofSize(size))
				.withMaxResults(maxResults)
				.build();

		var multiSearchQueryParameter = new ElasticsearchTemplate.MultiSearchQueryParameter(query, SampleEntity.class, IndexCoordinates.of("foo"));
		var multiSearchQueryParameters = List.of(multiSearchQueryParameter);

		var msearchRequest = requestConverter.searchMsearchRequest(multiSearchQueryParameters, null);

		var searchBody = msearchRequest.searches().get(0).body();
		var actualPageSize = searchBody.size();

		assertThat(actualPageSize).isNotEqualTo(size);
		assertThat(actualPageSize).isEqualTo(maxResults);
	}

	@Document(indexName = "does-not-matter")
	static class SampleEntity {
		@Nullable
		@Id private String id;
		@Nullable
		@Field(type = FieldType.Text) private String text;
	}
}
