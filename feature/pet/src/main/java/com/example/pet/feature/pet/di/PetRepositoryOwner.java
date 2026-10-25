package com.example.pet.feature.pet.di;
import com.example.pet.feature.pet.data.repository.PetRepository;
/** 由宿主 Application 实现，避免各页面创建独立的数据来源。 */
public interface PetRepositoryOwner { PetRepository getPetRepository(); }